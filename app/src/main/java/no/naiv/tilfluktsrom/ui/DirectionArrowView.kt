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
 */
class DirectionArrowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rotationAngle = 0f

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.shelter_primary)
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val arrowPath = Path()

    /**
     * Set the rotation angle in degrees.
     * 0 = pointing up (north/forward), positive = clockwise.
     */
    fun setDirection(degrees: Float) {
        rotationAngle = degrees
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val size = minOf(width, height) * 0.4f

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
}
