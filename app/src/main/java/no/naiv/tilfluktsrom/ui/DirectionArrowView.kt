package no.naiv.tilfluktsrom.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.cos
import kotlin.math.sin
import no.naiv.tilfluktsrom.R

/**
 * Custom view that displays a large directional arrow pointing toward
 * the target shelter. The arrow rotates based on the bearing to the
 * shelter relative to the device heading (compass).
 *
 * rotationAngle = shelterBearing - deviceHeading
 * This gives the direction the user needs to walk, adjusted for which
 * way they're currently facing.
 *
 * Optionally draws a discrete north indicator on the perimeter so users
 * can validate compass calibration against a known direction.
 *
 * Accessibility:
 *  - Exposes direction ("straight ahead" / "to the right" / ...) and
 *    distance to TalkBack via contentDescription, updated on every call
 *    to [setDirection] using the distance set via [setDistanceText].
 *  - Announces changes when the direction crosses a 45° sector boundary,
 *    throttled so rapid device rotation doesn't spam the screen reader.
 *
 * Reduced motion (WCAG 2.3.3):
 *  - When the user has set Android's animator duration scale to 0
 *    ("Remove animations"), the arrow snaps to the nearest 45° sector
 *    instead of rotating smoothly, removing the continuous motion that
 *    can trigger vestibular symptoms.
 */
class DirectionArrowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rotationAngle = 0f
    private var northAngle = Float.NaN

    /** Last distance text set by the owner (e.g. "1.2 km"). */
    private var distanceText: String = ""

    /** Controls whether this instance announces direction changes to
     *  TalkBack. The mini arrow in the bottom sheet sets this to false
     *  so the full-screen compass arrow is the only one that speaks. */
    var announceDirectionChanges: Boolean = true

    private var lastAnnouncedSector: Int = -1
    private var lastAnnounceTimeMs: Long = 0L

    /** True when the user has disabled OS animations. Read once; users
     *  rarely change this setting while the view is alive. */
    private val reduceMotion: Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.shelter_primary)
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99CFD8DC.toInt() // text_secondary at ~60% opacity
        style = Paint.Style.FILL
    }

    private val northTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99CFD8DC.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val arrowPath = Path()
    private val northPath = Path()

    /**
     * Set the distance text used in the accessibility description
     * (e.g. "1.2 km"). Should be called whenever the selected shelter
     * or user location changes. Does not trigger a redraw on its own;
     * the next [setDirection] call picks up the new text.
     */
    fun setDistanceText(text: String) {
        distanceText = text
    }

    /**
     * Set the rotation angle in degrees.
     * 0 = pointing up (toward the shelter if facing it), positive = clockwise.
     *
     * Also refreshes the accessibility description and, if the sector
     * changed, announces the new direction to TalkBack.
     */
    fun setDirection(degrees: Float) {
        // Visual: snap to 8 cardinal sectors when reduce-motion is on so
        // the arrow doesn't rotate continuously with device movement.
        val displayAngle = if (reduceMotion) snapToSector(degrees) else degrees
        if (displayAngle != rotationAngle) {
            rotationAngle = displayAngle
            invalidate()
        }

        updateAccessibility(degrees)
    }

    /**
     * Set the angle to north in the view's coordinate space.
     * This is typically -deviceHeading (where north is on screen).
     * Set to Float.NaN to hide the north indicator.
     */
    fun setNorthAngle(degrees: Float) {
        northAngle = degrees
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val size = minOf(width, height) * 0.4f

        // Draw north indicator first (behind the main arrow)
        if (!northAngle.isNaN()) {
            drawNorthIndicator(canvas, cx, cy, size)
        }

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)

        // Draw arrow pointing up
        arrowPath.reset()
        arrowPath.moveTo(cx, cy - size)                     // tip
        arrowPath.lineTo(cx + size * 0.5f, cy + size * 0.3f) // right
        arrowPath.lineTo(cx + size * 0.15f, cy + size * 0.1f)
        arrowPath.lineTo(cx + size * 0.15f, cy + size * 0.7f) // right tail
        arrowPath.lineTo(cx - size * 0.15f, cy + size * 0.7f) // left tail
        arrowPath.lineTo(cx - size * 0.15f, cy + size * 0.1f)
        arrowPath.lineTo(cx - size * 0.5f, cy + size * 0.3f) // left
        arrowPath.close()

        canvas.drawPath(arrowPath, arrowPaint)
        canvas.drawPath(arrowPath, outlinePaint)

        canvas.restore()
    }

    /**
     * Draw a small north indicator: a tiny triangle and "N" label pointing
     * outward from the view centre in the direction of north. The radius
     * is clamped so the label stays inside the viewport even when north
     * points toward the shorter viewport axis (previously the indicator
     * would be drawn off-screen when the user was facing roughly east or
     * west on a portrait-oriented compass view).
     */
    private fun drawNorthIndicator(canvas: Canvas, cx: Float, cy: Float, arrowSize: Float) {
        val tickSize = arrowSize * 0.1f
        val textSize = arrowSize * 0.18f
        northTextPaint.textSize = textSize

        // Outward reach of the rendered indicator beyond `radius`: the
        // triangle's apex sits at tickSize*1.8, the text baseline at
        // tickSize*2.2, and the "N" glyph extends roughly textSize above
        // its baseline. The larger of these is what must fit inside the
        // viewport.
        val labelReach = tickSize * 2.2f + textSize

        val radius = clampIndicatorRadius(
            cx, cy, width, height, northAngle,
            preferredRadius = arrowSize * 1.35f,
            labelReach = labelReach,
            minRadius = tickSize * 3f
        )

        canvas.save()
        canvas.rotate(northAngle, cx, cy)

        northPath.reset()
        northPath.moveTo(cx, cy - radius)
        northPath.lineTo(cx - tickSize, cy - radius - tickSize * 1.8f)
        northPath.lineTo(cx + tickSize, cy - radius - tickSize * 1.8f)
        northPath.close()
        canvas.drawPath(northPath, northPaint)

        canvas.drawText("N", cx, cy - radius - tickSize * 2.2f, northTextPaint)

        canvas.restore()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // Present as an ImageView so screen readers treat the description
        // as the full semantic content, not a container label.
        info.className = "android.widget.ImageView"
    }

    /**
     * Update [contentDescription] from the latest bearing and distance,
     * and announce the change if the user has crossed a 45° sector
     * boundary and enough time has passed since the last announcement.
     */
    private fun updateAccessibility(angleDegrees: Float) {
        val sector = sectorIndex(angleDegrees)
        val directionText = context.getString(sectorStringRes(sector))
        val description = context.getString(
            R.string.a11y_direction_with_distance,
            directionText,
            distanceText
        )
        contentDescription = description

        if (!announceDirectionChanges) return

        val now = SystemClock.uptimeMillis()
        if (sector != lastAnnouncedSector &&
            now - lastAnnounceTimeMs >= ANNOUNCE_INTERVAL_MS
        ) {
            lastAnnouncedSector = sector
            lastAnnounceTimeMs = now
            announceForAccessibility(description)
        }
    }

    private fun sectorStringRes(sector: Int): Int = when (sector) {
        0 -> R.string.a11y_dir_forward
        1 -> R.string.a11y_dir_forward_right
        2 -> R.string.a11y_dir_right
        3 -> R.string.a11y_dir_back_right
        4 -> R.string.a11y_dir_back
        5 -> R.string.a11y_dir_back_left
        6 -> R.string.a11y_dir_left
        7 -> R.string.a11y_dir_forward_left
        else -> R.string.a11y_dir_forward
    }

    companion object {
        /** Minimum gap between TalkBack announcements. Prevents a rapidly
         *  turning user from flooding the screen reader queue. */
        private const val ANNOUNCE_INTERVAL_MS = 750L

        /** Map a raw angle (any float) to one of 8 sectors, 0..7 starting
         *  at "forward" (0°) and going clockwise in 45° steps. Pure function;
         *  exposed as `internal` so it can be unit-tested on the JVM without
         *  the Android framework. */
        internal fun sectorIndex(angleDegrees: Float): Int {
            var a = angleDegrees % 360f
            if (a < 0f) a += 360f
            return (((a + 22.5f) / 45f).toInt()) % 8
        }

        /** Snap to the centre of the sector the current angle falls in.
         *  Returns a value in {0, 45, 90, 135, 180, 225, 270, 315}. */
        internal fun snapToSector(angleDegrees: Float): Float =
            sectorIndex(angleDegrees) * 45f

        /**
         * Return the largest radius from the view centre that still lets an
         * indicator — positioned on a circle around the centre and reaching
         * [labelReach] further in the outward direction — stay inside the
         * axis-aligned viewport `[0, viewWidth] × [0, viewHeight]`, while
         * respecting [preferredRadius] as an upper bound.
         *
         * [angleDegrees] is in screen space: 0° points up, positive is
         * clockwise. [minRadius] is a floor used only when the label's
         * reach is larger than the available room (a degenerate case we
         * shouldn't hit for real viewport sizes). Pure function; exposed
         * as `internal` so it can be unit-tested on the JVM.
         */
        internal fun clampIndicatorRadius(
            cx: Float,
            cy: Float,
            viewWidth: Int,
            viewHeight: Int,
            angleDegrees: Float,
            preferredRadius: Float,
            labelReach: Float,
            minRadius: Float
        ): Float {
            val thetaRad = Math.toRadians(angleDegrees.toDouble())
            val dx = sin(thetaRad).toFloat()
            val dy = -cos(thetaRad).toFloat()
            val tHoriz = when {
                dx > 0f -> (viewWidth - cx) / dx
                dx < 0f -> -cx / dx
                else -> Float.POSITIVE_INFINITY
            }
            val tVert = when {
                dy > 0f -> (viewHeight - cy) / dy
                dy < 0f -> -cy / dy
                else -> Float.POSITIVE_INFINITY
            }
            val distanceToEdge = minOf(tHoriz, tVert)
            return minOf(preferredRadius, distanceToEdge - labelReach)
                .coerceAtLeast(minRadius)
        }
    }
}
