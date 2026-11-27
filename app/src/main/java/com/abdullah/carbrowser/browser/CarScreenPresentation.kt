package com.abdullah.carbrowser.browser

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.abdullah.carbrowser.privacy.Privacy
import org.json.JSONObject
import kotlin.math.max

/**
 * The browser rendered on the car screen.
 *
 * Android Auto gives us a Surface; BrowserScreen wraps it in a VirtualDisplay, and this
 * Presentation puts real WebViews (one per tab) on that display under a Helium-style toolbar.
 * Input from the phone touchpad is injected here as synthetic touch / mouse events.
 */
class CarScreenPresentation(outer: Context, display: Display) : Presentation(outer, display), BrowserTab.Host {

    private val main = Handler(Looper.getMainLooper())

    private lateinit var content: FrameLayout
    private lateinit var chrome: CarChrome
    private lateinit var webArea: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var cursor: CursorView
    private lateinit var lockOverlay: LinearLayout
    private lateinit var lockTitle: TextView

    val tabs = ArrayList<BrowserTab>()
    var active: BrowserTab? = null
        private set

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    var theater = false
        private set

    private var surfaceW = 0
    private var surfaceH = 0
    private var visibleArea: Rect? = null

    var locked = false
        private set
    val isFullscreen get() = customView != null

    private var dragDownTime = -1L
    private var nextTapAt = 0L

    /** Video state of the active tab, polled while the phone's Media page is open. */
    class Media(val found: Boolean, val time: Double, val duration: Double, val paused: Boolean, val rate: Double, val muted: Boolean)
    var media: Media? = null
        private set

    // ---------------------------------------------------------------- setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        val ctx = context
        val d = ctx.resources.displayMetrics.density

        val root = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }
        content = FrameLayout(ctx)
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        chrome = CarChrome(ctx, chromeActions)
        webArea = FrameLayout(ctx)
        column.addView(chrome, LinearLayout.LayoutParams(MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        column.addView(webArea, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        fullscreenContainer = FrameLayout(ctx).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        cursor = CursorView(ctx)

        lockTitle = TextView(ctx).apply {
            text = "Paused while driving"
            setTextColor(CarTheme.fg)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * d)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        val lockSub = TextView(ctx).apply {
            text = "Audio keeps playing. Video comes back once you've been parked for a moment."
            setTextColor(CarTheme.dim)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 15 * d)
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (10 * d).toInt(), (24 * d).toInt(), 0)
        }
        val dot = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Prefs.accent) }
        }
        lockOverlay = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            isClickable = true
            visibility = View.GONE
            addView(dot, LinearLayout.LayoutParams((14 * d).toInt(), (14 * d).toInt()).apply {
                bottomMargin = (18 * d).toInt(); gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(lockTitle)
            addView(lockSub)
        }

        content.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(fullscreenContainer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(cursor, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(lockOverlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)

        restoreTabs()
        applyMargins()
        applySettings()
        setLocked(locked)
        main.post(mediaPoll)
    }

    private fun restoreTabs() {
        val saved = Prefs.tabUrls.ifEmpty { listOf(Prefs.lastUrl.ifBlank { Prefs.homePage }) }
        val activeIndex = Prefs.activeTab
        saved.forEach { addTab(it, select = false) }
        select(tabs[activeIndex.coerceIn(0, tabs.size - 1)])
    }

    private fun saveTabs() {
        Prefs.tabUrls = tabs.map { it.url }
        Prefs.activeTab = tabs.indexOf(active).coerceAtLeast(0)
        active?.let { Prefs.lastUrl = it.url }
    }

    // ---------------------------------------------------------------- tabs

    private fun addTab(url: String, select: Boolean): BrowserTab {
        val t = BrowserTab(context, this)
        t.webView.visibility = View.GONE
        webArea.addView(t.webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        tabs += t
        t.load(url)
        if (select) select(t)
        return t
    }

    private fun select(t: BrowserTab) {
        if (isFullscreen) exitFullscreen()
        if (theater) theater = false
        active = t
        tabs.forEach { it.webView.visibility = if (it === t) View.VISIBLE else View.GONE }
        t.webView.onResume()
        tabs.filter { it !== t }.forEach { it.webView.onPause() }
        refreshChrome()
        saveTabs()
        CarBrowser.notifyChanged()
    }

    fun newTab(url: String = Prefs.homePage) { addTab(Prefs.normalize(url), select = true) }

    fun selectTab(id: Int) { tabs.firstOrNull { it.id == id }?.let { select(it) } }

    fun nextTab() {
        if (tabs.size < 2) return
        val i = tabs.indexOf(active)
        select(tabs[(i + 1) % tabs.size])
    }

    fun closeTab(id: Int) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        val idx = tabs.indexOf(t)
        tabs.remove(t)
        webArea.removeView(t.webView)
        t.destroy()
        if (tabs.isEmpty()) addTab(Prefs.homePage, select = true)
        else if (t === active) select(tabs[idx.coerceAtMost(tabs.size - 1)])
        else { refreshChrome(); saveTabs(); CarBrowser.notifyChanged() }
    }

    private fun refreshChrome() {
        if (!::chrome.isInitialized) return
        chrome.update(tabs, active)
    }

    override fun onTabUpdated(tab: BrowserTab) {
        if (tab === active) refreshChrome()
        saveTabs()
        CarBrowser.notifyChanged()
    }

    override fun openInNewTab(url: String) {
        main.post { newTab(url) }
    }

    override fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) { callback.onCustomViewHidden(); return }
        customView = view
        customViewCallback = callback
        fullscreenContainer.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        fullscreenContainer.visibility = View.VISIBLE
        applyMargins()
        CarBrowser.notifyChanged()
    }

    override fun onHideFullscreen() = exitFullscreen()

    private val chromeActions = object : CarChrome.Actions {
        override fun back() = this@CarScreenPresentation.back()
        override fun forward() = this@CarScreenPresentation.forward()
        override fun reload() = this@CarScreenPresentation.reload()
        override fun home() = this@CarScreenPresentation.home()
        override fun newTab() = this@CarScreenPresentation.newTab()
        override fun nextTab() = this@CarScreenPresentation.nextTab()
        override fun selectTab(id: Int) = this@CarScreenPresentation.selectTab(id)
        override fun closeTab(id: Int) = this@CarScreenPresentation.closeTab(id)
    }

    fun destroy() {
        main.removeCallbacksAndMessages(null)
        if (::webArea.isInitialized) {
            saveTabs()
            tabs.forEach { t ->
                if (Prefs.wipeOnDisconnect) Privacy.clearAll(context, t.webView)
                t.destroy()
            }
            tabs.clear()
            BrowserTab.insecureAllowed.clear()
        }
        dismiss()
    }

    // ---------------------------------------------------------------- settings / layout

    /** Re-reads every setting (colours, toolbar, text size, desktop mode, cookies). */
    fun applySettings() {
        if (!::chrome.isInitialized) return
        chrome.applyTheme()
        chrome.visibility = if (Prefs.carToolbar && !theater) View.VISIBLE else View.GONE
        tabs.forEach { it.applySettings(context); it.refreshHome() }
        cursor.invalidate()
        refreshChrome()
    }

    fun setSurfaceSize(w: Int, h: Int) {
        surfaceW = w; surfaceH = h
        applyMargins()
    }

    fun setVisibleArea(area: Rect) {
        visibleArea = Rect(area)
        applyMargins()
    }

    /** Keep the page clear of Android Auto's own buttons, except in fullscreen video. */
    private fun applyMargins() {
        if (!::content.isInitialized) return
        val lp = content.layoutParams as FrameLayout.LayoutParams
        val r = visibleArea
        if (customView != null || theater || r == null || r.isEmpty || surfaceW == 0) {
            lp.setMargins(0, 0, 0, 0)
        } else {
            lp.setMargins(r.left, r.top, max(0, surfaceW - r.right), max(0, surfaceH - r.bottom))
        }
        content.layoutParams = lp
    }

    fun setLocked(on: Boolean) {
        locked = on
        if (!::lockOverlay.isInitialized) return
        if (on && dragDownTime >= 0) dragEnd()
        lockOverlay.visibility = if (on) View.VISIBLE else View.GONE
    }

    fun exitFullscreen() {
        val v = customView ?: return
        customView = null
        fullscreenContainer.removeView(v)
        fullscreenContainer.visibility = View.GONE
        val cb = customViewCallback
        customViewCallback = null
        cb?.onCustomViewHidden()
        applyMargins()
        CarBrowser.notifyChanged()
    }

    // ---------------------------------------------------------------- pointer input

    fun moveCursorBy(dx: Float, dy: Float) {
        if (locked || !::cursor.isInitialized) return
        cursor.moveBy(dx, dy)
        if (dragDownTime >= 0) sendTouch(MotionEvent.ACTION_MOVE, cursor.posX, cursor.posY, dragDownTime)
        else sendMouse(MotionEvent.ACTION_HOVER_MOVE, cursor.posX, cursor.posY)
    }

    fun clickAtCursor() {
        if (locked || !::cursor.isInitialized) return
        cursor.pulse()
        tap(cursor.posX, cursor.posY, 40)
    }

    fun longPressAtCursor() {
        if (locked || !::cursor.isInitialized) return
        cursor.pulse()
        tap(cursor.posX, cursor.posY, 800)
    }

    fun dragStart() {
        if (locked || dragDownTime >= 0 || !::cursor.isInitialized) return
        dragDownTime = SystemClock.uptimeMillis()
        sendTouch(MotionEvent.ACTION_DOWN, cursor.posX, cursor.posY, dragDownTime)
    }

    fun dragEnd() {
        if (dragDownTime < 0) return
        sendTouch(MotionEvent.ACTION_UP, cursor.posX, cursor.posY, dragDownTime)
        dragDownTime = -1L
    }

    /** Scroll from the phone. dx/dy = how far the page content should move, in car pixels. */
    fun scrollBy(dx: Float, dy: Float) {
        if (locked || !::cursor.isInitialized) return
        val vc = ViewConfiguration.get(context)
        val v = dy / vc.scaledVerticalScrollFactor
        val h = -dx / vc.scaledHorizontalScrollFactor
        sendMouse(MotionEvent.ACTION_SCROLL, cursor.posX, cursor.posY, v, h)
    }

    /** Tap on the car's own touchscreen (surface coordinates). */
    fun tapFromCar(sx: Float, sy: Float) {
        if (locked || !::content.isInitialized) return
        cursor.moveTo(sx - content.left, sy - content.top)
        cursor.pulse()
        tap(cursor.posX, cursor.posY, 40)
    }

    fun scrollFromCar(distanceX: Float, distanceY: Float) = scrollBy(-distanceX, -distanceY)

    private fun tap(x: Float, y: Float, holdMs: Long) {
        // Taps are queued so two quick clicks never overlap (double-click works).
        val start = max(SystemClock.uptimeMillis(), nextTapAt)
        nextTapAt = start + holdMs + 60
        main.postAtTime({
            val down = SystemClock.uptimeMillis()
            sendTouch(MotionEvent.ACTION_DOWN, x, y, down)
            main.postDelayed({
                sendTouch(MotionEvent.ACTION_UP, x, y, down)
                val wv = active?.webView ?: return@postDelayed
                val t = wv.hitTestResult.type
                if (t == WebView.HitTestResult.SRC_ANCHOR_TYPE || t == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) active?.allowNav()
            }, holdMs)
        }, start)
    }

    private fun sendTouch(action: Int, x: Float, y: Float, downTime: Long) {
        val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        content.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun sendMouse(action: Int, x: Float, y: Float, vScroll: Float = 0f, hScroll: Float = 0f) {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y
            setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
            setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
        }
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(
            now, now, action, 1, arrayOf(props), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
        )
        content.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    // ---------------------------------------------------------------- keyboard

    private val wv get() = active?.webView

    fun typeText(text: String) {
        val w = wv ?: return
        if (text.isEmpty()) return
        val js = """
            (function(t){
              var e=document.activeElement;
              if(!e||e===document.body||e.tagName==='IFRAME')return 'no';
              if(document.execCommand('insertText',false,t))return 'ok';
              if('value' in e){
                var s=e.selectionStart==null?e.value.length:e.selectionStart;
                var en=e.selectionEnd==null?s:e.selectionEnd;
                e.value=e.value.slice(0,s)+t+e.value.slice(en);
                e.dispatchEvent(new Event('input',{bubbles:true}));
                return 'ok';
              }
              return 'no';
            })(${JSONObject.quote(text)})
        """.trimIndent()
        w.evaluateJavascript(js) { r -> if (r != "\"ok\"") typeWithKeys(text) }
    }

    /** Fallback for text boxes inside iframes: real key events (English letters/numbers). */
    private fun typeWithKeys(text: String) {
        val w = wv ?: return
        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()) ?: return
        w.requestFocus()
        events.forEach { w.dispatchKeyEvent(it) }
    }

    fun backspace() {
        val js = "(function(){var e=document.activeElement;if(!e||e===document.body||e.tagName==='IFRAME')return 'no';" +
            "return document.execCommand('delete')?'ok':'no';})()"
        wv?.evaluateJavascript(js) { r -> if (r != "\"ok\"") sendKey(KeyEvent.KEYCODE_DEL) }
    }

    fun enter() {
        active?.allowNav()
        val js = "(function(){var e=document.activeElement;if(!e||e===document.body||e.tagName==='IFRAME')return 'no';" +
            "var o={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true};" +
            "var go=e.dispatchEvent(new KeyboardEvent('keydown',o));e.dispatchEvent(new KeyboardEvent('keyup',o));" +
            "if(go&&e.form){if(e.form.requestSubmit)e.form.requestSubmit();else e.form.submit();}return 'ok';})()"
        wv?.evaluateJavascript(js) { r -> if (r != "\"ok\"") sendKey(KeyEvent.KEYCODE_ENTER) }
    }

    fun sendKey(code: Int) {
        val w = wv ?: return
        w.requestFocus()
        val now = SystemClock.uptimeMillis()
        w.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        w.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    // ---------------------------------------------------------------- video

    private fun onVideo(body: String, fallback: (() -> Unit)? = null) {
        val js = "(function(){var v=document.querySelector('video');if(!v)return 'none';$body;return 'ok';})()"
        wv?.evaluateJavascript(js) { r -> if (r == "\"none\"") fallback?.invoke() }
    }

    /** Works on the page's own <video>; for players inside iframes, falls back to keyboard keys. */
    fun playPause() = onVideo("if(v.paused)v.play();else v.pause()") { sendKey(KeyEvent.KEYCODE_SPACE) }

    fun seek(seconds: Int) = onVideo("v.currentTime=Math.max(0,v.currentTime+($seconds))") {
        sendKey(if (seconds > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    fun seekTo(seconds: Double) = onVideo("v.currentTime=$seconds")
    fun setSpeed(rate: Float) = onVideo("document.querySelectorAll('video').forEach(function(x){x.playbackRate=$rate})")
    fun toggleMute() = onVideo("v.muted=!v.muted") { sendKey(KeyEvent.KEYCODE_M) }

    /** Theater: blows the biggest video or embedded player up to fill the whole car screen. */
    fun toggleTheater() {
        val js = """
            (function(){var id='__cb_theater';var s=document.getElementById(id);
            if(s){s.remove();var o=document.querySelector('[data-cbt]');if(o)o.removeAttribute('data-cbt');return 'off';}
            var best=null,area=0;document.querySelectorAll('video,iframe').forEach(function(e){
              var r=e.getBoundingClientRect();var a=r.width*r.height;if(a>area){area=a;best=e;}});
            if(!best||area<1000)return 'none';best.setAttribute('data-cbt','1');
            s=document.createElement('style');s.id=id;
            s.textContent='[data-cbt]{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;'+
              'max-width:none!important;max-height:none!important;z-index:2147483647!important;background:#000!important;'+
              'object-fit:contain;margin:0!important;border:0!important;transform:none!important}html,body{overflow:hidden!important}';
            document.documentElement.appendChild(s);return 'on';})()
        """.trimIndent()
        wv?.evaluateJavascript(js) { r ->
            theater = r == "\"on\""
            if (r == "\"none\"") CarBrowser.showNotice("No video found on this page", null)
            applySettings()
            applyMargins()
            CarBrowser.notifyChanged()
        }
    }

    private val mediaPoll = object : Runnable {
        override fun run() {
            if (CarBrowser.mediaWatchers > 0 && !locked) {
                val js = "(function(){var v=document.querySelector('video');if(!v)return null;" +
                    "return {t:v.currentTime||0,d:isFinite(v.duration)?v.duration:0,p:v.paused,r:v.playbackRate,m:v.muted};})()"
                wv?.evaluateJavascript(js) { r ->
                    media = try {
                        if (r == null || r == "null") Media(false, 0.0, 0.0, true, 1.0, false)
                        else JSONObject(r).let {
                            Media(true, it.optDouble("t"), it.optDouble("d"), it.optBoolean("p"), it.optDouble("r", 1.0), it.optBoolean("m"))
                        }
                    } catch (_: Exception) { null }
                    CarBrowser.notifyChanged()
                }
            }
            main.postDelayed(this, 1000)
        }
    }

    // ---------------------------------------------------------------- navigation

    fun back() {
        if (customView != null) { exitFullscreen(); return }
        if (theater) { toggleTheater(); return }
        active?.back()
    }

    fun forward() { active?.forward() }
    fun reload() { active?.reload() }
    fun home() { active?.load(Prefs.homePage) }
    fun loadUrl(url: String) { active?.load(url) ?: newTab(url) }

    fun openAnyway(url: String, insecureHost: String?) { active?.openAnyway(url, insecureHost) }

    fun clearData() {
        tabs.forEach { Privacy.clearAll(context, it.webView) }
        active?.reload()
    }

    fun zoom(zoomIn: Boolean) { wv?.let { if (zoomIn) it.zoomIn() else it.zoomOut() } }
}
