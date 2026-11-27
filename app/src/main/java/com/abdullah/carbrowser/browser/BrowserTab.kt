package com.abdullah.carbrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.abdullah.carbrowser.privacy.FilterLists
import com.abdullah.carbrowser.privacy.Privacy
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** One browser tab: a WebView plus the shields / redirect guard / HTTPS-only logic for it. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class BrowserTab(ctx: Context, private val host: Host) {

    interface Host {
        fun onTabUpdated(tab: BrowserTab)
        fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
        fun onHideFullscreen()
        fun openInNewTab(url: String)
    }

    val id = nextId++
    val webView = WebView(ctx)

    var title = ""; private set
    var url = Prefs.HOME; private set
    var progress = 100; private set
    var isSecure = false; private set
    private val blockedCount = AtomicInteger(0)
    val blocked get() = blockedCount.get()

    val isHome get() = url == Prefs.HOME
    val displayTitle get() = when {
        isHome -> "New tab"
        title.isNotBlank() -> title
        else -> Library.hostOf(url)
    }

    // Redirect guard: the site you're on, and until when a cross-site navigation is allowed
    // (set when YOU click a link, type an address, press Enter, or tap "Open anyway").
    @Volatile private var pageHost: String? = null
    private var allowNavUntil = 0L
    private val upgradedHosts = HashSet<String>()
    private val styleId = "s" + java.util.UUID.randomUUID().toString().replace("-", "").take(10)
    private val shields get() = Prefs.shields

    fun applySettings(ctx: Context) {
        val ua = Privacy.userAgent(ctx, Prefs.desktopMode)
        val uaChanged = webView.settings.userAgentString != ua
        webView.settings.userAgentString = ua
        webView.settings.textZoom = Prefs.carTextZoom
        Privacy.applyCookies(webView, Prefs.cookies)
        if (uaChanged && !isHome && webView.url != null) webView.reload()
    }

    // ---------------------------------------------------------------- navigation

    fun allowNav() { allowNavUntil = SystemClock.uptimeMillis() + 5000 }

    fun load(input: String) {
        allowNav()
        if (input == Prefs.HOME) { showHome(); return }
        var u = Uri.parse(input)
        val h = u.host?.lowercase()
        if (u.scheme == "http" && h != null && h !in insecureAllowed) {
            upgradedHosts += h
            u = u.buildUpon().scheme("https").build()
        }
        webView.loadUrl(u.toString(), Privacy.HEADERS)
    }

    fun showHome() {
        url = Prefs.HOME; title = ""; isSecure = true
        blockedCount.set(0)
        webView.loadDataWithBaseURL(null, StartPage.html(), "text/html", "utf-8", null)
        host.onTabUpdated(this)
    }

    /** Re-draws the start page (after bookmarks or colours change). */
    fun refreshHome() { if (isHome) webView.loadDataWithBaseURL(null, StartPage.html(), "text/html", "utf-8", null) }

    fun openAnyway(target: String, insecureHost: String?) {
        if (insecureHost != null) insecureAllowed += insecureHost
        load(target)
    }

    fun back(): Boolean { allowNav(); return if (webView.canGoBack()) { webView.goBack(); true } else false }
    fun forward() { allowNav(); if (webView.canGoForward()) webView.goForward() }
    fun reload() { allowNav(); if (isHome) refreshHome() else webView.reload() }
    val canGoBack get() = webView.canGoBack()
    val canGoForward get() = webView.canGoForward()

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    private fun injectCosmetics(h: String?) {
        if (h == null || !shields) return
        val css = FilterLists.engine.cssFor(h)
        if (css.isEmpty()) return
        val js = "(function(c){if(document.getElementById('$styleId'))return;var s=document.createElement('style');" +
            "s.id='$styleId';s.textContent=c;(document.head||document.documentElement).appendChild(s);})(" +
            JSONObject.quote(css) + ")"
        webView.evaluateJavascript(js, null)
    }

    private fun isRealPage(u: String?) = u != null && (u.startsWith("http://") || u.startsWith("https://"))

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url
            val scheme = target.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return true          // intent://, market:// etc.
            val h = target.host?.lowercase() ?: return true

            if (!request.isForMainFrame) {
                return shields && FilterLists.engine.shouldBlock(target, pageHost)
            }
            if (shields && FilterLists.engine.shouldBlock(target, null)) {
                blockedCount.incrementAndGet()
                CarBrowser.showNotice("Blocked ad/tracker page: $h", null)
                return true
            }
            val current = pageHost
            val crossSite = current != null && Privacy.site(h) != Privacy.site(current)
            if (shields && crossSite && SystemClock.uptimeMillis() > allowNavUntil) {
                blockedCount.incrementAndGet()
                CarBrowser.showNotice("Blocked redirect to $h", target.toString())
                return true
            }
            if (scheme == "http" && h !in insecureAllowed) {
                upgradedHosts += h
                view.loadUrl(target.buildUpon().scheme("https").build().toString(), Privacy.HEADERS)
                return true
            }
            return false
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!shields) return null
            val first = if (request.isForMainFrame) null else pageHost
            if (!FilterLists.engine.shouldBlock(request.url, first)) return null
            blockedCount.incrementAndGet()
            return EMPTY()
        }

        override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
            if (isRealPage(u)) {
                val h = Uri.parse(u).host?.lowercase()
                if (h != pageHost) blockedCount.set(0)
                pageHost = h
                url = u!!
                isSecure = u.startsWith("https://")
            } else {
                pageHost = null
            }
            host.onTabUpdated(this@BrowserTab)
        }

        override fun onPageCommitVisible(view: WebView, u: String?) {
            if (isRealPage(u)) injectCosmetics(Uri.parse(u).host)
        }

        override fun onPageFinished(view: WebView, u: String?) {
            if (isRealPage(u)) injectCosmetics(Uri.parse(u).host)
            host.onTabUpdated(this@BrowserTab)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            val h = request.url.host?.lowercase() ?: return
            if (request.isForMainFrame && request.url.scheme == "https" && h in upgradedHosts) {
                CarBrowser.showNotice(
                    "$h has no secure (HTTPS) version",
                    request.url.buildUpon().scheme("http").build().toString(),
                    insecureHost = h
                )
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
            if (isRealPage(u)) {
                url = u!!
                isSecure = u.startsWith("https://")
            } else if (u == null || u.startsWith("about:") || u.startsWith("data:")) {
                url = Prefs.HOME; title = ""
            }
            host.onTabUpdated(this@BrowserTab)
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onShowCustomView(view: View, callback: CustomViewCallback) = host.onShowFullscreen(view, callback)
        override fun onHideCustomView() = host.onHideFullscreen()

        // Pop-ups never open; a real link with target=_blank opens in a new tab (like a desktop browser).
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            if (isUserGesture) {
                val hit = view.hitTestResult
                val link = hit.extra
                val isLink = hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                if (isLink && link != null && link.startsWith("http") &&
                    !(shields && FilterLists.engine.shouldBlock(Uri.parse(link), null))
                ) host.openInNewTab(link)
            }
            return false
        }

        override fun onPermissionRequest(request: PermissionRequest) = request.deny()

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onReceivedTitle(view: WebView, t: String?) {
            if (!isHome) {
                title = t ?: ""
                if (isRealPage(view.url)) Library.addHistory(title, view.url!!)
            }
            host.onTabUpdated(this@BrowserTab)
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress
            host.onTabUpdated(this@BrowserTab)
        }

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    // Runs last, after the clients above exist.
    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            safeBrowsingEnabled = false
            setGeolocationEnabled(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            @Suppress("DEPRECATION")
            saveFormData = false
        }
        webView.setBackgroundColor(if (Prefs.amoled) 0xFF000000.toInt() else 0xFF161616.toInt())
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, Privacy.DOC_START_JS, setOf("*"))
        }
        applySettings(ctx)
        webView.webViewClient = client
        webView.webChromeClient = chrome
    }

    companion object {
        private var nextId = 1
        /** Hosts you allowed over plain http (shared by all tabs, forgotten on disconnect). */
        val insecureAllowed = HashSet<String>()
        private fun EMPTY() = WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
    }
}
