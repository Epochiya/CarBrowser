package com.abdullah.carbrowser.phone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.abdullah.carbrowser.browser.CarBrowser
import com.abdullah.carbrowser.phone.ui.RemoteApp
import com.abdullah.carbrowser.phone.ui.UiState

/** The phone side: a Remote Mouse-style remote for the browser on the car screen. */
class MainActivity : ComponentActivity() {

    private val listener = CarBrowser.Listener { UiState.bump() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { RemoteApp() }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION
            )
        }
    }

    override fun onStart() {
        super.onStart()
        CarBrowser.addListener(listener)
        UiState.bump()
    }

    override fun onStop() {
        CarBrowser.removeListener(listener)
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            CarBrowser.onLocationPermissionGranted?.invoke()
        }
        UiState.bump()
    }

    companion object { private const val REQ_LOCATION = 1 }
}
