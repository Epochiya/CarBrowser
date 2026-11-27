package com.abdullah.carbrowser.browser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Bookmarks and history, stored only on the phone. */
object Library {
    data class Entry(val title: String, val url: String, val time: Long = System.currentTimeMillis())

    private const val MAX_HISTORY = 300
    private lateinit var sp: SharedPreferences

    private val DEFAULT_BOOKMARKS = listOf(
        Entry("YouTube", "https://m.youtube.com"),
        Entry("Crunchyroll", "https://www.crunchyroll.com"),
        Entry("DuckDuckGo", "https://duckduckgo.com"),
    )

    fun init(c: Context) {
        if (::sp.isInitialized) return
        sp = c.applicationContext.getSharedPreferences("library", Context.MODE_PRIVATE)
        if (!sp.contains("bookmarks")) save("bookmarks", DEFAULT_BOOKMARKS)
    }

    private fun load(key: String): List<Entry> = runCatching {
        val a = JSONArray(sp.getString(key, "[]"))
        List(a.length()) {
            val o = a.getJSONObject(it)
            Entry(o.optString("t"), o.optString("u"), o.optLong("d"))
        }
    }.getOrDefault(emptyList())

    private fun save(key: String, list: List<Entry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("t", it.title).put("u", it.url).put("d", it.time)) }
        sp.edit().putString(key, a.toString()).apply()
    }

    val bookmarks: List<Entry> get() = load("bookmarks")
    val history: List<Entry> get() = load("history")

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    /** Adds the page, or removes it if it's already bookmarked. Returns true if it's now bookmarked. */
    fun toggleBookmark(title: String, url: String): Boolean {
        val list = bookmarks.toMutableList()
        val existing = list.indexOfFirst { it.url == url }
        return if (existing >= 0) {
            list.removeAt(existing); save("bookmarks", list); false
        } else {
            list.add(Entry(title.ifBlank { hostOf(url) }, url)); save("bookmarks", list); true
        }
    }

    fun removeBookmark(url: String) = save("bookmarks", bookmarks.filterNot { it.url == url })

    fun moveBookmark(from: Int, to: Int) {
        val list = bookmarks.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        save("bookmarks", list)
    }

    fun addHistory(title: String, url: String) {
        if (!Prefs.keepHistory) return
        val list = history.toMutableList()
        list.removeAll { it.url == url }
        list.add(0, Entry(title, url))
        save("history", list.take(MAX_HISTORY))
    }

    fun clearHistory() = save("history", emptyList())

    /** Suggestions for the address bar: bookmarks first, then history. */
    fun suggest(query: String, limit: Int = 8): List<Entry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return (bookmarks + history)
            .filter { it.url.lowercase().contains(q) || it.title.lowercase().contains(q) }
            .distinctBy { it.url }
            .take(limit)
    }

    fun hostOf(url: String): String =
        runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url
}
