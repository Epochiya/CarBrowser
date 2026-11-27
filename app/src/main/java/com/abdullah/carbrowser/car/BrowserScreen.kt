package com.abdullah.carbrowser.car

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.browser.CarScreenPresentation
import com.abdullah.carbrowser.browser.DrivingLock
import kotlin.math.abs

/**
 * The one screen shown on Android Auto. It uses the navigation template, which is the
 * template that hands us a raw drawing surface; the browser is rendered onto that surface.
 */
class BrowserScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarScreenPresentation? = null
    private val lock = DrivingLock { CarBrowser.setLockState(it) }
    private var carSpeedRegistered = false
    private var gpsRegistered = false

    init {
        com.abdullah.carbrowser.privacy.FilterLists.init(carContext)
        lifecycle.addObserver(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback())
    }

    // ---------------------------------------------------------------- surface

    private fun surfaceCallback() = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            val surface = container.surface ?: return
            val w = container.width
            val h = container.height
            val dpi = container.dpi
            if (w <= 0 || h <= 0) return

            val vd = virtualDisplay
            if (vd == null) {
                val dm = carContext.getSystemService(DisplayManager::class.java)!!
                val created = dm.createVirtualDisplay(
                    "CarBrowser", w, h, dpi, surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                )
                virtualDisplay = created
                val p = CarScreenPresentation(carContext, created.display)
                presentation = p
                p.setSurfaceSize(w, h)
                p.show()
                CarBrowser.attach(p)
            } else {
                // Keep the same browser (and its page) when Android Auto recreates the surface.
                vd.resize(w, h, dpi)
                vd.surface = surface
                presentation?.setSurfaceSize(w, h)
            }
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            presentation?.setVisibleArea(visibleArea)
        }

        override fun onStableAreaChanged(stableArea: Rect) {}

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            virtualDisplay?.surface = null
        }

        // Car touchscreen: tap = click at that spot, drag (in pan mode) = scroll.
        override fun onClick(x: Float, y: Float) {
            presentation?.tapFromCar(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            presentation?.scrollFromCar(distanceX, distanceY)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {}
        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {}
    }

    // ---------------------------------------------------------------- template

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Back").setOnClickListener { CarBrowser.back() }.build())
            .addAction(Action.Builder().setTitle("Play/Pause").setOnClickListener { CarBrowser.playPause() }.build())
            .addAction(Action.Builder().setTitle("Theater").setOnClickListener { CarBrowser.toggleTheater() }.build())
            .build()
        val mapStrip = ActionStrip.Builder().addAction(Action.PAN).build()
        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setMapActionStrip(mapStrip)
            .build()
    }

    // ---------------------------------------------------------------- speed sources

    private val carSpeedListener = OnCarDataAvailableListener<Speed> { speed ->
        val v = speed.rawSpeedMetersPerSecond
        if (v.status == CarValue.STATUS_SUCCESS) v.value?.let { lock.onCarSpeed(abs(it)) }
    }

    private val gpsListener = LocationListener { loc ->
        if (loc.hasSpeed()) lock.onGpsSpeed(loc.speed)
    }

    private fun startCarSpeed() {
        if (carSpeedRegistered || carContext.carAppApiLevel < CarAppApiLevels.LEVEL_3) return
        try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
                .addSpeedListener(ContextCompat.getMainExecutor(carContext), carSpeedListener)
            carSpeedRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Car speed not available", e)
        }
    }

    private fun startGps() {
        if (gpsRegistered) return
        val granted = ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            val lm = carContext.getSystemService(LocationManager::class.java)!!
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener, Looper.getMainLooper())
            gpsRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "GPS not available", e)
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        lock.start()
        startCarSpeed()
        startGps()
        CarBrowser.onLocationPermissionGranted = { startGps() }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        lock.stop()
        CarBrowser.onLocationPermissionGranted = null
        if (carSpeedRegistered) {
            try {
                carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(carSpeedListener)
            } catch (_: Exception) {}
        }
        if (gpsRegistered) {
            carContext.getSystemService(LocationManager::class.java)?.removeUpdates(gpsListener)
        }
        presentation?.let {
            CarBrowser.detach(it)
            it.destroy()
        }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        CarBrowser.setLockState(DrivingLock.State.UNKNOWN)
    }

    companion object { private const val TAG = "CarBrowser" }
}
