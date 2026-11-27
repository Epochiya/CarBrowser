package com.abdullah.carbrowser.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.browser.Prefs
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Remote Mouse-style touchpad.
 *  - 1 finger drag ............ move cursor
 *  - tap ...................... click            - double-tap ....... double-click
 *  - two-finger tap ........... right click (long-press)
 *  - tap, then hold & drag .... drag (seek bars, sliders, selecting text)
 *  - hold still ............... long-press
 *  - 2 finger drag ............ scroll
 */
class TouchpadView(context: Context) : View(context) {

    var padColor = 0xFF1E1F24.toInt()
    var dotColor = 0xFF2C2E35.toInt()
    var accentColor = 0xFF8AB4F8.toInt()
    var hintColor = 0xFF6B7080.toInt()
    var showHints = true

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val density = resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var multi = false
    private var multiMoved = false
    private var multiDownTime = 0L
    private var longPressed = false
    private var maybeDrag = false
    private var dragging = false
    private var touching = false
    private var glowX = 0f
    private var glowY = 0f

    private val pendingClick = Runnable { CarBrowser.click() }
    private val longPress = Runnable {
        if (!moved && !multi && !maybeDrag) {
            longPressed = true
            buzz(HapticFeedbackConstants.LONG_PRESS)
            CarBrowser.longPress()
        }
    }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 13 * density }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 15 * density; isFakeBoldText = true
    }
    private val lines = listOf(
        "Drag to move  ·  Tap to click",
        "Two fingers to scroll  ·  Two-finger tap = right click",
        "Double-tap & hold, then drag to drag",
    )

    init {
        contentDescription = "Touchpad. Drag to move the cursor on the car screen, tap to click."
        isHapticFeedbackEnabled = true
    }

    private fun buzz(type: Int = HapticFeedbackConstants.CLOCK_TICK) {
        if (Prefs.haptics) performHapticFeedback(type)
    }

    override fun onDraw(canvas: Canvas) {
        val r = 28 * density
        bg.color = padColor
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, bg)

        // Subtle dot grid, like a real trackpad texture.
        dot.color = dotColor
        val step = 22 * density
        var y = step
        while (y < height - step / 2) {
            var x = step
            while (x < width - step / 2) { canvas.drawCircle(x, y, 1.3f * density, dot); x += step }
            y += step
        }

        if (touching) {
            val rad = 70 * density
            glow.shader = RadialGradient(glowX, glowY, rad, (accentColor and 0x00FFFFFF) or 0x40000000,
                accentColor and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(glowX, glowY, rad, glow)
        }

        if (showHints && !touching) {
            title.color = hintColor
            hint.color = hintColor
            val stepY = hint.textSize * 1.9f
            var ty = height / 2f - stepY * lines.size / 2f
            canvas.drawText("Touchpad", width / 2f, ty, title)
            ty += stepY * 1.2f
            for (l in lines) { canvas.drawText(l, width / 2f, ty, hint); ty += stepY }
        }
    }

    /** Phone pixels -> car pixels, with optional acceleration for fast swipes. */
    private fun accel(d: Float): Float {
        val dp = d / density
        val boost = if (Prefs.acceleration) 1f + min(abs(dp), 30f) / 12f else 1.6f
        return dp * SPEED * Prefs.sensitivity * boost
    }

    private fun scroll(dx: Float, dy: Float) {
        val k = SCROLL_SPEED * Prefs.scrollSpeed / density * (if (Prefs.naturalScroll) 1f else -1f)
        CarBrowser.scroll(dx * k, dy * k)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        glowX = e.x; glowY = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touching = true
                lastX = e.x; lastY = e.y; downX = e.x; downY = e.y; downTime = e.eventTime
                moved = false; multi = false; multiMoved = false; longPressed = false; dragging = false
                maybeDrag = main.hasCallbacks(pendingClick)       // second touch soon after a tap
                if (maybeDrag) main.removeCallbacks(pendingClick)
                main.postDelayed(longPress, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                main.removeCallbacks(longPress)
                if (maybeDrag) { CarBrowser.click(); maybeDrag = false }   // give back the first tap
                if (dragging) { CarBrowser.dragEnd(); dragging = false }
                if (!multi) multiDownTime = e.eventTime
                multi = true
                avg(e, -1).let { lastX = it.first; lastY = it.second }
            }

            MotionEvent.ACTION_MOVE -> {
                if (multi) {
                    val (ax, ay) = avg(e, -1)
                    if (e.pointerCount >= 2) {
                        if (hypot(ax - lastX, ay - lastY) > 0.5f) multiMoved = true
                        scroll(ax - lastX, ay - lastY)
                    }
                    lastX = ax; lastY = ay
                    invalidate()
                    return true
                }
                if (!moved && hypot(e.x - downX, e.y - downY) > slop) {
                    moved = true
                    main.removeCallbacks(longPress)
                    if (maybeDrag) { dragging = true; buzz(HapticFeedbackConstants.LONG_PRESS); CarBrowser.dragStart() }
                }
                if (moved) CarBrowser.moveCursor(accel(e.x - lastX), accel(e.y - lastY))
                lastX = e.x; lastY = e.y
            }

            MotionEvent.ACTION_POINTER_UP -> {
                avg(e, e.actionIndex).let { lastX = it.first; lastY = it.second }
            }

            MotionEvent.ACTION_UP -> {
                touching = false
                main.removeCallbacks(longPress)
                val tapClicks = Prefs.tapToClick
                when {
                    dragging -> CarBrowser.dragEnd()
                    multi -> if (!multiMoved && e.eventTime - multiDownTime < TAP_MS * 2) {
                        buzz(HapticFeedbackConstants.CONTEXT_CLICK); CarBrowser.longPress()      // two-finger tap
                    }
                    longPressed -> Unit
                    !tapClicks -> Unit
                    !moved && maybeDrag -> { buzz(); CarBrowser.click(); CarBrowser.click() }   // double-click
                    !moved && e.eventTime - downTime < TAP_MS -> { buzz(); main.postDelayed(pendingClick, DOUBLE_TAP_MS) }
                }
                dragging = false; maybeDrag = false
            }

            MotionEvent.ACTION_CANCEL -> {
                touching = false
                main.removeCallbacks(longPress)
                if (dragging) CarBrowser.dragEnd()
                dragging = false; maybeDrag = false
            }
        }
        invalidate()
        return true
    }

    /** Average position of all pointers, skipping [skip] (the one being lifted). */
    private fun avg(e: MotionEvent, skip: Int): Pair<Float, Float> {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            sx += e.getX(i); sy += e.getY(i); n++
        }
        return if (n == 0) lastX to lastY else (sx / n) to (sy / n)
    }

    companion object {
        const val SPEED = 1.3f
        const val SCROLL_SPEED = 2.2f
        const val TAP_MS = 250L
        const val DOUBLE_TAP_MS = 230L
        const val LONG_PRESS_MS = 600L
    }
}
