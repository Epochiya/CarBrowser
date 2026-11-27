package com.abdullah.carbrowser.browser

import android.content.Context
import android.content.SharedPreferences
import com.abdullah.carbrowser.privacy.Privacy
import org.json.JSONArray

/** Every setting in the app. Changing one goes through [CarBrowser.settingsChanged] so both screens update. */
object Prefs {
    const val HOME = "about:home"

    enum class Engine(val label: String, val template: String) {
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
        STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s"),
        GOOGLE("Google", "https://www.google.com/search?q=%s"),
    }

    /** Accent colours offered in Settings (ARGB). */
    val ACCENTS = listOf(
        0xFF8AB4F8.toInt(), // Helium blue
        0xFFB39DFF.toInt(), // violet
        0xFF7FD6A4.toInt(), // mint
        0xFFFFB86B.toInt(), // amber
        0xFFFF8A9A.toInt(), // rose
        0xFF6FD3E8.toInt(), // cyan
        0xFFE6E6E6.toInt(), // mono
    )

    private lateinit var sp: SharedPreferences
    fun init(c: Context) {
        if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("carbrowser", Context.MODE_PRIVATE)
    }

    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun f(k: String, d: Float) = sp.getFloat(k, d)
    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(block: SharedPreferences.Editor.() -> Unit) = sp.edit().apply(block).apply()

    // ---- browsing
    var homePage: String
        get() = s("home", HOME)
        set(v) = put { putString("home", v.ifBlank { HOME }) }
    var engine: Engine
        get() = runCatching { Engine.valueOf(s("engine", Engine.DUCKDUCKGO.name)) }.getOrDefault(Engine.DUCKDUCKGO)
        set(v) = put { putString("engine", v.name) }
    var desktopMode: Boolean
        get() = b("desktop", false)
        set(v) = put { putBoolean("desktop", v) }
    var keepHistory: Boolean
        get() = b("keep_history", true)
        set(v) = put { putBoolean("keep_history", v) }
    /** Open tabs, saved so they come back next time you connect. */
    var tabUrls: List<String>
        get() = runCatching {
            val a = JSONArray(s("tabs", "[]"))
            List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
        set(v) = put { putString("tabs", JSONArray(v).toString()) }
    var activeTab: Int
        get() = i("active_tab", 0)
        set(v) = put { putInt("active_tab", v) }

    // ---- privacy
    var shields: Boolean
        get() = b("shields", true)
        set(v) = put { putBoolean("shields", v) }
    var cookies: Privacy.Cookies
        get() = if (s("cookies", "off") == "session") Privacy.Cookies.SESSION else Privacy.Cookies.OFF
        set(v) = put { putString("cookies", if (v == Privacy.Cookies.SESSION) "session" else "off") }
    var wipeOnDisconnect: Boolean
        get() = b("wipe", true)
        set(v) = put { putBoolean("wipe", v) }

    // ---- look
    var accent: Int
        get() = i("accent", ACCENTS[0])
        set(v) = put { putInt("accent", v) }
    var amoled: Boolean
        get() = b("amoled", false)
        set(v) = put { putBoolean("amoled", v) }
    var carToolbar: Boolean
        get() = b("car_toolbar", true)
        set(v) = put { putBoolean("car_toolbar", v) }
    var carTabStrip: Boolean
        get() = b("car_tabs", true)
        set(v) = put { putBoolean("car_tabs", v) }
    /** Text size on the car screen, in percent. */
    var carTextZoom: Int
        get() = i("text_zoom", 100)
        set(v) = put { putInt("text_zoom", v) }
    var cursorSize: Float
        get() = f("cursor_size", 1f)
        set(v) = put { putFloat("cursor_size", v) }

    // ---- touchpad
    var sensitivity: Float
        get() = f("sensitivity", 1f)
        set(v) = put { putFloat("sensitivity", v) }
    var acceleration: Boolean
        get() = b("accel", true)
        set(v) = put { putBoolean("accel", v) }
    var scrollSpeed: Float
        get() = f("scroll_speed", 1f)
        set(v) = put { putFloat("scroll_speed", v) }
    var naturalScroll: Boolean
        get() = b("natural", true)
        set(v) = put { putBoolean("natural", v) }
    var haptics: Boolean
        get() = b("haptics", true)
        set(v) = put { putBoolean("haptics", v) }
    var tapToClick: Boolean
        get() = b("tap_click", true)
        set(v) = put { putBoolean("tap_click", v) }
    var showHints: Boolean
        get() = b("hints", true)
        set(v) = put { putBoolean("hints", v) }
    var clickButtons: Boolean
        get() = b("click_buttons", true)
        set(v) = put { putBoolean("click_buttons", v) }
    var scrollStrip: Boolean
        get() = b("scroll_strip", true)
        set(v) = put { putBoolean("scroll_strip", v) }

    // ---- media
    var seekStep: Int
        get() = i("seek_step", 10)
        set(v) = put { putInt("seek_step", v) }
    var skipIntro: Int
        get() = i("skip_intro", 85)
        set(v) = put { putInt("skip_intro", v) }

    // ---- remembered between launches
    var lastUrl: String
        get() = s("last_url", HOME)
        set(v) = put { putString("last_url", v) }

    /** Turns whatever was typed into a URL, or a search with the chosen engine. */
    fun normalize(input: String): String {
        val t = input.trim()
        if (t.isEmpty() || t == HOME) return HOME
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        val looksLikeUrl = !t.contains(' ') && t.contains('.') && !t.endsWith('.')
        return if (looksLikeUrl) "https://$t"
        else engine.template.replace("%s", java.net.URLEncoder.encode(t, "UTF-8"))
    }
}
