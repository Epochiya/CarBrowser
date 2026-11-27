package com.abdullah.carbrowser.browser

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.abdullah.carbrowser.R

/** Colours shared by the car-screen UI (Helium's calm neutral greys + your accent). */
object CarTheme {
    val bg get() = if (Prefs.amoled) 0xFF000000.toInt() else 0xFF1B1B1B.toInt()
    val surface get() = if (Prefs.amoled) 0xFF141414.toInt() else 0xFF262626.toInt()
    val surfaceHi get() = if (Prefs.amoled) 0xFF1F1F1F.toInt() else 0xFF303030.toInt()
    const val fg = 0xFFE8E8E8.toInt()
    const val dim = 0xFF9A9A9A.toInt()
    const val line = 0xFF333333.toInt()
    const val warn = 0xFFFFB86B.toInt()
    val accent get() = Prefs.accent
}

/**
 * Helium-style browser chrome on the car screen: an optional compact tab strip, then one slim
 * toolbar (back, forward, reload, address pill with shield count, tab counter, home) and a thin
 * accent loading line.
 */
class CarChrome(ctx: Context, private val actions: Actions) : LinearLayout(ctx) {

    interface Actions {
        fun back(); fun forward(); fun reload(); fun home(); fun newTab(); fun nextTab()
        fun selectTab(id: Int); fun closeTab(id: Int)
    }

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private val stripScroll = HorizontalScrollView(ctx)
    private val strip = LinearLayout(ctx)
    private val bar = LinearLayout(ctx)
    private val backBtn = icon(R.drawable.ic_back, "Back") { actions.back() }
    private val fwdBtn = icon(R.drawable.ic_forward, "Forward") { actions.forward() }
    private val reloadBtn = icon(R.drawable.ic_refresh, "Reload") { actions.reload() }
    private val pill = LinearLayout(ctx)
    private val secureIcon = ImageView(ctx)
    private val address = TextView(ctx)
    private val shieldIcon = ImageView(ctx)
    private val shieldCount = TextView(ctx)
    private val tabCount = TextView(ctx)
    private val homeBtn = icon(R.drawable.ic_home, "Home") { actions.home() }
    private val progressTrack = FrameLayout(ctx)
    private val progress = View(ctx)

    private var stripKey = ""

    init {
        orientation = VERTICAL

        // ---- tab strip
        stripScroll.isHorizontalScrollBarEnabled = false
        strip.orientation = HORIZONTAL
        strip.setPadding(dp(6f), dp(5f), dp(6f), 0)
        stripScroll.addView(strip)
        addView(stripScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ---- toolbar
        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(6f), dp(5f), dp(6f), dp(5f))
        bar.addView(backBtn); bar.addView(fwdBtn); bar.addView(reloadBtn)

        pill.orientation = HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.setPadding(dp(12f), 0, dp(10f), 0)
        secureIcon.layoutParams = LayoutParams(dp(14f), dp(14f)).apply { marginEnd = dp(8f) }
        address.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(13.5f).toFloat())
        address.setSingleLine()
        address.ellipsize = TextUtils.TruncateAt.END
        address.setTextColor(CarTheme.dim)
        shieldIcon.setImageResource(R.drawable.ic_shield)
        shieldIcon.layoutParams = LayoutParams(dp(14f), dp(14f)).apply { marginStart = dp(8f) }
        shieldCount.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12f).toFloat())
        shieldCount.setTextColor(CarTheme.dim)
        shieldCount.setPadding(dp(4f), 0, 0, 0)
        pill.addView(secureIcon)
        pill.addView(address, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        pill.addView(shieldIcon)
        pill.addView(shieldCount)
        bar.addView(pill, LayoutParams(0, dp(32f), 1f).apply { marginStart = dp(4f); marginEnd = dp(4f) })

        tabCount.gravity = Gravity.CENTER
        tabCount.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12f).toFloat())
        tabCount.typeface = Typeface.DEFAULT_BOLD
        tabCount.contentDescription = "Next tab"
        tabCount.setOnClickListener { actions.nextTab() }
        val tcWrap = FrameLayout(ctx).apply {
            addView(tabCount, FrameLayout.LayoutParams(dp(20f), dp(20f), Gravity.CENTER))
            setOnClickListener { actions.nextTab() }
        }
        bar.addView(tcWrap, LayoutParams(dp(36f), dp(36f)))
        bar.addView(homeBtn)
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ---- loading line
        progress.pivotX = 0f
        progressTrack.addView(progress, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(progressTrack, LayoutParams(LayoutParams.MATCH_PARENT, dp(2f)))

        applyTheme()
    }

    private fun round(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * d
        if (stroke != null) setStroke(dp(1.5f), stroke)
    }

    private fun icon(res: Int, label: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        contentDescription = label
        val p = dp(8f)
        setPadding(p, p, p, p)
        layoutParams = LayoutParams(dp(36f), dp(36f))
        background = round(0, 8f)
        setOnClickListener { onClick() }
    }

    fun applyTheme() {
        setBackgroundColor(CarTheme.bg)
        pill.background = round(CarTheme.surface, 9f)
        tabCount.background = round(0, 5f, CarTheme.dim)
        tabCount.setTextColor(CarTheme.fg)
        progress.setBackgroundColor(CarTheme.accent)
        shieldIcon.imageTintList = ColorStateList.valueOf(CarTheme.accent)
        listOf(backBtn, fwdBtn, reloadBtn, homeBtn).forEach { it.imageTintList = ColorStateList.valueOf(CarTheme.fg) }
        stripKey = ""
    }

    fun update(tabs: List<BrowserTab>, active: BrowserTab?) {
        stripScroll.visibility = if (Prefs.carTabStrip && tabs.size > 1) VISIBLE else GONE
        val key = tabs.joinToString("|") { "${it.id}:${it.displayTitle}" } + "#" + active?.id
        if (key != stripKey) { stripKey = key; rebuildStrip(tabs, active) }

        val t = active ?: return
        backBtn.alpha = if (t.canGoBack) 1f else 0.35f
        fwdBtn.alpha = if (t.canGoForward) 1f else 0.35f

        // Address: domain bright, the rest dim (like Helium / Chrome's simplified URL).
        val sb = SpannableStringBuilder()
        if (t.isHome) {
            sb.append("Search or type a URL on your phone")
            secureIcon.setImageResource(R.drawable.ic_search)
            secureIcon.imageTintList = ColorStateList.valueOf(CarTheme.dim)
        } else {
            val uri = android.net.Uri.parse(t.url)
            val host = uri.host?.removePrefix("www.") ?: t.url
            sb.append(host)
            sb.setSpan(ForegroundColorSpan(CarTheme.fg), 0, host.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, host.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val rest = (uri.encodedPath ?: "").takeIf { it.length > 1 }.orEmpty()
            if (rest.isNotEmpty()) sb.append(rest)
            if (t.title.isNotBlank()) sb.append("  ·  ").append(t.title)
            secureIcon.setImageResource(if (t.isSecure) R.drawable.ic_lock else R.drawable.ic_warning)
            secureIcon.imageTintList = ColorStateList.valueOf(if (t.isSecure) CarTheme.dim else CarTheme.warn)
        }
        address.text = sb

        val shieldsOn = Prefs.shields
        shieldIcon.alpha = if (shieldsOn) 1f else 0.3f
        shieldCount.text = if (!shieldsOn) "off" else if (t.blocked > 0) t.blocked.toString() else ""
        shieldCount.visibility = if (shieldCount.text.isEmpty()) GONE else VISIBLE

        tabCount.text = if (tabs.size > 99) ":D" else tabs.size.toString()

        val loading = t.progress in 0..99
        progress.visibility = if (loading) VISIBLE else INVISIBLE
        progress.scaleX = (t.progress.coerceIn(5, 100)) / 100f
    }

    private fun rebuildStrip(tabs: List<BrowserTab>, active: BrowserTab?) {
        strip.removeAllViews()
        for (t in tabs) {
            val isActive = t === active
            val chip = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), 0, dp(4f), 0)
                background = round(if (isActive) CarTheme.surfaceHi else 0, 8f)
                setOnClickListener { actions.selectTab(t.id) }
                contentDescription = "Tab: ${t.displayTitle}"
            }
            if (isActive) {
                chip.addView(View(context).apply { background = round(CarTheme.accent, 3f) },
                    LayoutParams(dp(6f), dp(6f)).apply { marginEnd = dp(8f) })
            }
            chip.addView(TextView(context).apply {
                text = t.displayTitle
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END
                maxWidth = dp(170f)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(12.5f).toFloat())
                setTextColor(if (isActive) CarTheme.fg else CarTheme.dim)
            })
            chip.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(CarTheme.dim)
                val p = dp(7f); setPadding(p, p, p, p)
                contentDescription = "Close tab"
                setOnClickListener { actions.closeTab(t.id) }
            }, LayoutParams(dp(26f), dp(26f)))
            strip.addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(30f)).apply { marginEnd = dp(4f) })
        }
        strip.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            imageTintList = ColorStateList.valueOf(CarTheme.dim)
            val p = dp(7f); setPadding(p, p, p, p)
            contentDescription = "New tab"
            setOnClickListener { actions.newTab() }
        }, LayoutParams(dp(30f), dp(30f)))
    }
}
