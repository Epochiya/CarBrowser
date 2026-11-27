package com.abdullah.carbrowser.browser

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Mouse pointer drawn on the car screen, with an accent ripple on click. Fades after a few idle seconds. */
class CursorView(context: Context) : View(context) {
    var posX = -1f; private set
    var posY = -1f; private set

    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val scale get() = density * 1.4f * Prefs.cursorSize
    private val arrow = Path().apply {
        moveTo(0f, 0f); lineTo(0f, 17f); lineTo(4.5f, 13f); lineTo(7.5f, 19.5f)
        lineTo(10f, 18.5f); lineTo(7.2f, 12.2f); lineTo(12.5f, 12.2f); close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.2f; strokeJoin = Paint.Join.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; style = Paint.Style.FILL }
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var rippleT = -1f
    private var rippleAnim: ValueAnimator? = null
    private val hide = Runnable { animate().alpha(0f).setDuration(400).start() }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (posX < 0 || posX >= w || posY >= h) { posX = w / 2f; posY = h / 2f }
        invalidate()
    }

    fun moveBy(dx: Float, dy: Float) = moveTo(posX + dx, posY + dy)

    fun moveTo(x: Float, y: Float) {
        posX = x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        posY = y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        wake()
        invalidate()
    }

    fun wake() {
        removeCallbacks(hide)
        animate().cancel()
        alpha = 1f
        postDelayed(hide, 3500)
    }

    /** Little accent ring where you clicked. */
    fun pulse() {
        wake()
        rippleAnim?.cancel()
        rippleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            addUpdateListener { rippleT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (posX < 0) return
        if (rippleT in 0f..0.999f) {
            ripple.color = Prefs.accent
            ripple.alpha = ((1f - rippleT) * 140).toInt()
            canvas.drawCircle(posX, posY, (6f + rippleT * 18f) * density * Prefs.cursorSize, ripple)
        }
        canvas.save()
        canvas.translate(posX + 1.5f * density, posY + 2f * density)
        canvas.scale(scale, scale)
        canvas.drawPath(arrow, shadow)
        canvas.restore()
        canvas.save()
        canvas.translate(posX, posY)
        canvas.scale(scale, scale)
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, stroke)
        canvas.restore()
    }
}
