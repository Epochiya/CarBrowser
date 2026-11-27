package com.abdullah.carbrowser.browser

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Decides whether the car is parked, using car speed (from Android Auto) and phone GPS speed.
 *  - Any speed above MOVING_MPS locks the screen immediately.
 *  - It unlocks only after the car has been stopped for UNLOCK_DELAY_MS, so a red light
 *    doesn't turn the video back on.
 *  - If speed data drops out while moving (tunnel, garage), it stays locked.
 */
class DrivingLock(private val onChange: (State) -> Unit) {
    enum class State { PARKED, MOVING, UNKNOWN }

    companion object {
        const val MOVING_MPS = 2.0f          // ~7 km/h
        const val STOPPED_MPS = 0.7f         // ~2.5 km/h (GPS jitter while standing still)
        const val UNLOCK_DELAY_MS = 45_000L  // must be stopped this long before video comes back
        const val STALE_MS = 15_000L
    }

    var state = State.UNKNOWN
        private set

    private var carSpeed = 0f
    private var carAt = 0L
    private var gpsSpeed = 0f
    private var gpsAt = 0L
    private var stoppedSince = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { evaluate(); handler.postDelayed(this, 1000) }
    }

    fun start() { handler.post(tick) }
    fun stop() { handler.removeCallbacks(tick) }

    fun onCarSpeed(mps: Float) { carSpeed = mps; carAt = SystemClock.elapsedRealtime(); evaluate() }
    fun onGpsSpeed(mps: Float) { gpsSpeed = mps; gpsAt = SystemClock.elapsedRealtime(); evaluate() }

    private fun evaluate() {
        val now = SystemClock.elapsedRealtime()
        val speeds = buildList {
            if (carAt > 0 && now - carAt < STALE_MS) add(carSpeed)
            if (gpsAt > 0 && now - gpsAt < STALE_MS) add(gpsSpeed)
        }
        val next = if (speeds.isEmpty()) {
            if (state == State.MOVING) State.MOVING else State.UNKNOWN
        } else {
            val v = speeds.max()
            when {
                v >= MOVING_MPS -> { stoppedSince = 0L; State.MOVING }
                v <= STOPPED_MPS -> {
                    if (stoppedSince == 0L) stoppedSince = now
                    if (state == State.MOVING && now - stoppedSince < UNLOCK_DELAY_MS) State.MOVING
                    else State.PARKED
                }
                else -> { stoppedSince = 0L; if (state == State.MOVING) State.MOVING else State.PARKED }
            }
        }
        if (next != state) { state = next; onChange(next) }
    }
}
