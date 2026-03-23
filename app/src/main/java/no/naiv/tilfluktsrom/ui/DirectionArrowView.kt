package no.naiv.tilfluktsrom.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
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
 */
class DirectionArrowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rotationAngle = 0f
    private var northAngle = Float.NaN

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
     * Set the rotation angle in degrees.
     * 0 = pointing up (north/forward), positive = clockwise.
     */
    fun setDirection(degrees: Float) {
        rotationAngle = degrees
        invalidate()
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
     * Draw a small north indicator: a tiny triangle and "N" label
     * placed on the perimeter of the view, pointing inward toward center.
     */
    private fun drawNorthIndicator(canvas: Canvas, cx: Float, cy: Float, arrowSize: Float) {
        val radius = arrowSize * 1.35f
        val tickSize = arrowSize * 0.1f

        // Scale "N" text relative to the view
        northTextPaint.textSize = arrowSize * 0.18f

        canvas.save()
        canvas.rotate(northAngle, cx, cy)

        // Small triangle at the top of the perimeter circle
        northPath.reset()
        northPath.moveTo(cx, cy - radius)
        northPath.lineTo(cx - tickSize, cy - radius - tickSize * 1.8f)
        northPath.lineTo(cx + tickSize, cy - radius - tickSize * 1.8f)
        northPath.close()
        canvas.drawPath(northPath, northPaint)

        // "N" label just outside the triangle
        canvas.drawText("N", cx, cy - radius - tickSize * 2.2f, northTextPaint)

        canvas.restore()
    }
}
