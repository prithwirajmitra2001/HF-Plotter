package com.example.hfecgplotter

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView

class VerticalTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private val topDown: Boolean

    init {
        // Determine if the text should be drawn top→down or bottom→up based on gravity
        val currentGravity = gravity
        val verticalGravity = currentGravity and Gravity.VERTICAL_GRAVITY_MASK

        if (Gravity.isVertical(currentGravity) && verticalGravity == Gravity.BOTTOM) {
            // If gravity is BOTTOM, shift it to TOP and draw text bottom→up
            gravity = (currentGravity and Gravity.HORIZONTAL_GRAVITY_MASK) or Gravity.TOP
            topDown = false
        } else {
            // Otherwise, draw text top→down
            topDown = true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Swap width and height specs so that the TextView measures itself rotated
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        // After measuring, swap measured dimensions
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        if (topDown) {
            // Shift the canvas to the right edge, then rotate 90° clockwise
            canvas.translate(width.toFloat(), 0f)
            canvas.rotate(90f)
        } else {
            // Shift the canvas down to the bottom edge, then rotate 90° counter‐clockwise
            canvas.translate(0f, height.toFloat())
            canvas.rotate(-90f)
        }

        // Apply the compound padding and extended padding offsets before drawing the text
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())

        // Draw the text layout onto the rotated canvas
        layout.draw(canvas)
    }
}