package com.abdullah.carbrowser.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase

/** LibreWolf-style hardening for the WebView. */
object Privacy {

    /** Sent with every page load the app starts (address bar, links opened by the browser). */
    val HEADERS = mapOf("DNT" to "1", "Sec-GPC" to "1")

    /**
     * Runs at the start of every page and frame:
     *  - tells sites Do Not Track + Global Privacy Control are on
     *  - window.open does nothing (no pop-ups / pop-unders)
     *  - removes the Battery API (used for fingerprinting)
     */
    const val DOC_START_JS = """
(function(){try{
  var N=Navigator.prototype;
  Object.defineProperty(N,'doNotTrack',{get:function(){return '1'},configurable:true});
  Object.defineProperty(N,'globalPrivacyControl',{get:function(){return true},configurable:true});
  try{delete N.getBattery;}catch(e){}
  Object.defineProperty(N,'getBattery',{value:undefined,configurable:true});
  window.open=function(){return null;};
}catch(e){}})();
"""

    /** Cookie modes. OFF = refuse every cookie. SESSION = first-party only, wiped on disconnect. */
    enum class Cookies { OFF, SESSION }

    fun applyCookies(webView: WebView, mode: Cookies) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(mode == Cookies.SESSION)
        cm.setAcceptThirdPartyCookies(webView, false)
        if (mode == Cookies.OFF) cm.removeAllCookies(null)
    }

    /**
     * A generic user agent with no phone model and no "wv" WebView marker, so you look like
     * any other Chrome user (Chrome itself reports "Android 10; K" for everyone).
     */
    fun userAgent(ctx: Context, desktop: Boolean): String {
        val def = try { WebSettings.getDefaultUserAgent(ctx) } catch (_: Exception) { "" }
        val major = Regex("Chrome/(\\d+)").find(def)?.groupValues?.get(1) ?: "130"
        return if (desktop)
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
        else
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
    }

    /** "video.example.co.uk" -> "example.co.uk". Good enough to tell same-site from cross-site. */
    fun site(host: String): String {
        val labels = host.lowercase().removePrefix("www.").split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val tld = labels.last()
        val sld = labels[labels.size - 2]
        val take = if (tld.length == 2 && sld in setOf("co", "com", "net", "org", "gov", "edu", "ac", "ne", "or")) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    /** Wipe cookies, site storage, cache, history and form data. Called when you disconnect. */
    fun clearAll(ctx: Context, webView: WebView?) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        GeolocationPermissions.getInstance().clearAll()
        @Suppress("DEPRECATION")
        WebViewDatabase.getInstance(ctx).apply {
            clearHttpAuthUsernamePassword()
            clearFormData()
        }
        webView?.apply {
            clearCache(true)
            clearHistory()
            clearFormData()
            clearSslPreferences()
        }
    }
}
