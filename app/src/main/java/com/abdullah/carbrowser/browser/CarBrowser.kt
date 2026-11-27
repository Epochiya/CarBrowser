package com.abdullah.carbrowser.browser

import com.abdullah.carbrowser.privacy.FilterLists

/**
 * Shared link between the car side (BrowserScreen + CarScreenPresentation) and the
 * phone side (the Compose remote). Everything runs on the main thread.
 */
object CarBrowser {
    fun interface Listener { fun onStateChanged() }

    private val listeners = LinkedHashSet<Listener>()
    fun addListener(l: Listener) { listeners += l }
    fun removeListener(l: Listener) { listeners -= l }
    fun notifyChanged() = listeners.toList().forEach { it.onStateChanged() }

    var presentation: CarScreenPresentation? = null
        private set
    val isConnected get() = presentation != null

    var lockState = DrivingLock.State.UNKNOWN
        private set

    /** How many phone screens want live video info (the Media page). */
    var mediaWatchers = 0

    data class TabInfo(val id: Int, val title: String, val url: String, val active: Boolean, val isHome: Boolean)

    val tabs: List<TabInfo>
        get() = presentation?.let { p ->
            p.tabs.map { TabInfo(it.id, it.displayTitle, it.url, it === p.active, it.isHome) }
        } ?: Prefs.tabUrls.mapIndexed { i, u ->
            TabInfo(-1 - i, if (u == Prefs.HOME) "New tab" else Library.hostOf(u), u, i == Prefs.activeTab, u == Prefs.HOME)
        }

    val pageUrl get() = presentation?.active?.url ?: Prefs.lastUrl
    val pageTitle get() = presentation?.active?.let { if (it.isHome) "" else it.title } ?: ""
    val isHome get() = pageUrl == Prefs.HOME
    val blockedCount get() = presentation?.active?.blocked ?: 0
    val progress get() = presentation?.active?.progress ?: 100
    val canGoBack get() = presentation?.active?.canGoBack ?: false
    val canGoForward get() = presentation?.active?.canGoForward ?: false
    val media get() = presentation?.media
    val theater get() = presentation?.theater ?: false
    val fullscreen get() = presentation?.isFullscreen ?: false

    /** Something the browser blocked that you may want to allow (shown on the phone). */
    class Notice(val text: String, val url: String?, val insecureHost: String?, val time: Long = System.currentTimeMillis())
    var notice: Notice? = null
        private set

    init { FilterLists.onChanged = { notifyChanged() } }

    fun showNotice(text: String, url: String?, insecureHost: String? = null) {
        notice = Notice(text, url, insecureHost)
        notifyChanged()
    }

    fun dismissNotice() { notice = null; notifyChanged() }

    fun openAnyway() {
        val n = notice ?: return
        notice = null
        n.url?.let { presentation?.openAnyway(it, n.insecureHost) }
        notifyChanged()
    }

    /** Call after changing anything in [Prefs]. */
    fun settingsChanged() {
        presentation?.applySettings()
        notifyChanged()
    }

    fun setShields(on: Boolean) {
        Prefs.shields = on
        presentation?.reload()
        settingsChanged()
    }

    fun clearData() { presentation?.clearData() }

    /** Set by the car screen so the phone can tell it location permission was just granted. */
    var onLocationPermissionGranted: (() -> Unit)? = null

    fun attach(p: CarScreenPresentation) {
        presentation = p
        p.setLocked(lockState == DrivingLock.State.MOVING)
        notifyChanged()
    }

    fun detach(p: CarScreenPresentation) {
        if (presentation === p) presentation = null
        notifyChanged()
    }

    fun setLockState(s: DrivingLock.State) {
        lockState = s
        presentation?.setLocked(s == DrivingLock.State.MOVING)
        notifyChanged()
    }

    // --- pointer ---------------------------------------------------------
    fun moveCursor(dx: Float, dy: Float) { presentation?.moveCursorBy(dx, dy) }
    fun click() { presentation?.clickAtCursor() }
    fun longPress() { presentation?.longPressAtCursor() }
    fun dragStart() { presentation?.dragStart() }
    fun dragEnd() { presentation?.dragEnd() }
    fun scroll(dx: Float, dy: Float) { presentation?.scrollBy(dx, dy) }

    // --- keyboard --------------------------------------------------------
    fun typeText(t: String) { presentation?.typeText(t) }
    fun backspace() { presentation?.backspace() }
    fun enter() { presentation?.enter() }
    fun key(code: Int) { presentation?.sendKey(code) }

    // --- navigation ------------------------------------------------------
    fun back() { presentation?.back() }
    fun forward() { presentation?.forward() }
    fun reload() { presentation?.reload() }
    fun home() { presentation?.home() ?: run { Prefs.lastUrl = Prefs.homePage; notifyChanged() } }
    fun zoom(zoomIn: Boolean) { presentation?.zoom(zoomIn) }

    fun loadUrl(input: String) {
        val url = Prefs.normalize(input)
        val p = presentation
        if (p != null) p.loadUrl(url)
        else {
            // Not on the car yet: it opens there next time you connect.
            val list = Prefs.tabUrls.toMutableList()
            val i = Prefs.activeTab
            if (i in list.indices) list[i] = url else { list.add(url); Prefs.activeTab = list.size - 1 }
            Prefs.tabUrls = list
            Prefs.lastUrl = url
            notifyChanged()
        }
    }

    fun newTab(input: String? = null) {
        val url = input?.let { Prefs.normalize(it) } ?: Prefs.homePage
        val p = presentation
        if (p != null) p.newTab(url)
        else {
            Prefs.tabUrls = Prefs.tabUrls + url
            Prefs.activeTab = Prefs.tabUrls.size - 1
            notifyChanged()
        }
    }

    fun selectTab(id: Int) {
        val p = presentation
        if (p != null) p.selectTab(id)
        else { Prefs.activeTab = -1 - id; notifyChanged() }
    }

    fun closeTab(id: Int) {
        val p = presentation
        if (p != null) p.closeTab(id)
        else {
            val list = Prefs.tabUrls.toMutableList()
            val i = -1 - id
            if (i in list.indices) list.removeAt(i)
            Prefs.tabUrls = list
            Prefs.activeTab = Prefs.activeTab.coerceIn(0, (list.size - 1).coerceAtLeast(0))
            notifyChanged()
        }
    }

    fun closeAllTabs() {
        val p = presentation
        if (p != null) p.tabs.map { it.id }.forEach { p.closeTab(it) }
        else { Prefs.tabUrls = emptyList(); Prefs.activeTab = 0; notifyChanged() }
    }

    fun toggleBookmark(): Boolean {
        if (isHome) return false
        val now = Library.toggleBookmark(pageTitle, pageUrl)
        presentation?.tabs?.forEach { it.refreshHome() }
        notifyChanged()
        return now
    }

    // --- media -----------------------------------------------------------
    fun playPause() { presentation?.playPause() }
    fun seek(seconds: Int) { presentation?.seek(seconds) }
    fun seekTo(seconds: Double) { presentation?.seekTo(seconds) }
    fun setSpeed(rate: Float) { presentation?.setSpeed(rate) }
    fun toggleMute() { presentation?.toggleMute() }
    fun toggleTheater() { presentation?.toggleTheater() }
    fun exitFullscreen() { presentation?.exitFullscreen() }
}
