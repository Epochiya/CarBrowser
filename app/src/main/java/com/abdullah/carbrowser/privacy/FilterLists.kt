package com.abdullah.carbrowser.privacy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Downloads and keeps the filter lists fresh (like Brave / uBlock), then builds the FilterEngine.
 * Lists live in the app's private storage and refresh every 4 days, or when you tap "Update lists".
 */
object FilterLists {
    private class Source(val file: String, vararg val urls: String)

    private val SOURCES = listOf(
        Source("easylist.txt", "https://easylist.to/easylist/easylist.txt",
            "https://secure.fanboy.co.nz/easylist.txt"),
        Source("easyprivacy.txt", "https://easylist.to/easylist/easyprivacy.txt",
            "https://secure.fanboy.co.nz/easyprivacy.txt"),
        Source("ublock-filters.txt", "https://ublockorigin.github.io/uAssets/filters/filters.txt"),
        Source("ublock-privacy.txt", "https://ublockorigin.github.io/uAssets/filters/privacy.txt"),
        Source("ublock-badware.txt", "https://ublockorigin.github.io/uAssets/filters/badware.txt"),
        Source("peter-lowe.txt",
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext"),
        Source("cookie-banners.txt", "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt",
            "https://easylist-downloads.adblockplus.org/easylist-cookie.txt"),
    )

    private const val MAX_AGE_MS = 4L * 24 * 60 * 60 * 1000

    @Volatile var engine: FilterEngine = FilterEngine.builtin()
        private set
    @Volatile var lastUpdated = 0L
        private set
    @Volatile var updating = false
        private set

    var onChanged: (() -> Unit)? = null

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "filters").apply { mkdirs() }

    /** Load cached lists (fast), then update in the background if they're old. Safe to call often. */
    fun init(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        io.execute {
            rebuild(app)
            if (System.currentTimeMillis() - lastUpdated > MAX_AGE_MS) download(app)
        }
    }

    fun updateNow(ctx: Context) {
        val app = ctx.applicationContext
        io.execute { download(app) }
    }

    private fun download(ctx: Context) {
        updating = true
        notifyChanged()
        val d = dir(ctx)
        var any = false
        for (s in SOURCES) {
            for (u in s.urls) {
                if (fetch(u, File(d, s.file))) { any = true; break }
            }
        }
        updating = false
        if (any) rebuild(ctx) else notifyChanged()
    }

    private fun fetch(url: String, dest: File): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "CarBrowser-filter-updater")
        if (c.responseCode != 200) {
            c.disconnect(); false
        } else {
            val tmp = File(dest.path + ".tmp")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            c.disconnect()
            if (tmp.length() > 1000) tmp.renameTo(dest) else { tmp.delete(); false }
        }
    } catch (e: Exception) {
        Log.w("CarBrowser", "Filter download failed: $url", e)
        false
    }

    private fun rebuild(ctx: Context) {
        val files = dir(ctx).listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
        if (files.isNotEmpty()) {
            val texts = files.map { it.readText() } + FilterEngine.BUILTIN
            engine = FilterEngine.parse(texts)
            lastUpdated = files.maxOf { it.lastModified() }
        }
        notifyChanged()
    }

    private fun notifyChanged() = main.post { onChanged?.invoke() }
}
