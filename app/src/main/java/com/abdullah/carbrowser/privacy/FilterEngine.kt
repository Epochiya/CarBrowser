package com.abdullah.carbrowser.privacy

import android.net.Uri

/**
 * Ad / tracker blocker that reads standard filter lists (EasyList, EasyPrivacy, uBlock, hosts files).
 *
 * Supported (this is what does almost all of the work in uBlock / Brave):
 *  - Network rules:  ||ads.example.com^   ||example.com^$third-party   ||example.com/ads/
 *  - Exceptions:     @@||example.com^     @@||site.com^$generichide
 *  - Hosts files:    0.0.0.0 tracker.example.com
 *  - Cosmetic rules: ##.ad-banner        example.com##.sidebar-ad
 * Skipped: uBlock scriptlets (+js), procedural cosmetics (:has-text), $domain=/$redirect/$removeparam
 * rules, and wildcard substring rules.
 */
class FilterEngine private constructor(
    private val block: HashMap<String, Int>,
    private val allow: HashSet<String>,
    private val pathBlock: HashMap<String, MutableList<String>>,
    private val genericCss: String,
    private val siteSelectors: HashMap<String, MutableList<String>>,
    private val noGenericCosmetic: HashSet<String>,
    val ruleCount: Int
) {
    /** True if a request to [url] should be blocked. [pageHost] = site you're on (for third-party rules). */
    fun shouldBlock(url: Uri, pageHost: String?): Boolean {
        val host = url.host?.lowercase() ?: return false
        if (matchSuffix(allow, host)) return false
        val thirdParty = pageHost != null && Privacy.site(host) != Privacy.site(pageHost)
        var h = host
        while (true) {
            block[h]?.let { f -> if (f and ANY != 0 || (f and THIRD != 0 && thirdParty)) return true }
            pathBlock[h]?.let { list ->
                val path = url.encodedPath ?: ""
                if (list.any { path.startsWith(it) }) return true
            }
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** CSS that hides ads / cookie banners on [host]. */
    fun cssFor(host: String): String {
        val sb = StringBuilder()
        if (!matchSuffix(noGenericCosmetic, host)) sb.append(genericCss)
        var h = host.lowercase()
        while (true) {
            siteSelectors[h]?.forEach { sb.append(it).append(HIDE) }
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        return sb.toString()
    }

    private fun matchSuffix(set: Set<String>, host: String): Boolean {
        if (set.isEmpty()) return false
        var h = host
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    companion object {
        private const val ANY = 1
        private const val THIRD = 2
        private const val HIDE = "{display:none!important}\n"

        private val SKIP_OPTIONS = listOf(
            "domain=", "from=", "to=", "badfilter", "redirect", "removeparam", "csp", "~third-party", "~3p",
            "1p", "first-party", "strict1p", "denyallow", "header", "permissions", "uritransform",
            "replace", "urlskip", "method="
        )
        private val PROCEDURAL = listOf(
            ":has-text(", ":-abp-", ":matches-css", ":upward(", ":xpath(", ":remove(", ":style(",
            ":watch-attr(", ":min-text-length(", ":matches-path(", ":others(", ":matches-attr(",
            ":matches-prop(", ":remove-attr(", ":remove-class(", ":matches-media("
        )
        private val HIDE_EXCEPTION_OPTS = setOf("generichide", "ghide", "elemhide", "ehide", "document", "doc")

        /** Small list used until the full lists have downloaded. */
        val BUILTIN = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
            "google-analytics.com", "googletagmanager.com", "popads.net", "popcash.net", "propellerads.com",
            "propellerclick.com", "onclickads.net", "onclkds.com", "exoclick.com", "exosrv.com",
            "adsterra.com", "adsterratech.com", "juicyads.com", "hilltopads.net", "hilltopads.com",
            "a-ads.com", "adnxs.com", "taboola.com", "outbrain.com", "mgid.com", "trafficjunky.net",
            "revcontent.com", "clickadu.com", "adcash.com", "admaven.com", "ad-maven.com", "richpush.co",
            "pushance.com", "monetag.com", "galaksion.com", "tsyndicate.com", "zeydoo.com", "acint.net",
            "bidgear.com", "yandexadexchange.net", "scorecardresearch.com", "hotjar.com", "facebook.net",
            "connect.facebook.net", "criteo.com", "criteo.net", "amazon-adsystem.com", "quantserve.com"
        ).joinToString("\n") { "||$it^" }

        fun builtin(): FilterEngine = parse(listOf(BUILTIN))

        fun parse(texts: List<String>): FilterEngine {
            val block = HashMap<String, Int>(200_000)
            val allow = HashSet<String>()
            val pathBlock = HashMap<String, MutableList<String>>()
            val generic = LinkedHashSet<String>()
            val site = HashMap<String, MutableList<String>>()
            val noGeneric = HashSet<String>()
            var count = 0

            for (text in texts) for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line[0] == '!' || line[0] == '[' || line.startsWith("# ")) continue

                // hosts-file format
                if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
                    val d = line.substringAfter(' ').trim().substringBefore(' ').substringBefore('#').lowercase()
                    if (d.contains('.') && d != "0.0.0.0" && d != "localhost") { block[d] = ANY; count++ }
                    continue
                }

                // cosmetic
                if (line.contains("#@#") || line.contains("#?#") || line.contains("#$#") || line.contains("#%#")) continue
                val hashIdx = line.indexOf("##")
                if (hashIdx >= 0) {
                    val domains = line.substring(0, hashIdx)
                    val sel = line.substring(hashIdx + 2).trim()
                    if (sel.isEmpty() || sel.startsWith("+js") || sel.startsWith("^") ||
                        sel.contains('{') || sel.contains('}') || sel.contains('<') ||
                        PROCEDURAL.any { sel.contains(it) }
                    ) continue
                    if (domains.isEmpty()) generic += sel
                    else domains.split(',').forEach { d ->
                        val dd = d.trim().lowercase()
                        if (dd.isNotEmpty() && !dd.startsWith('~') && !dd.contains('*') && !dd.contains('/')) {
                            site.getOrPut(dd) { mutableListOf() }.add(sel)
                        }
                    }
                    count++
                    continue
                }

                // network
                val exception = line.startsWith("@@")
                var rule = if (exception) line.substring(2) else line
                if (!rule.startsWith("||")) continue
                val dollar = rule.indexOf('$')
                val opts = if (dollar >= 0) rule.substring(dollar + 1).lowercase() else ""
                rule = if (dollar >= 0) rule.substring(2, dollar) else rule.substring(2)

                val parts = if (opts.isEmpty()) emptyList() else opts.split(',').map { it.trim() }
                val end = rule.indexOfFirst { it == '^' || it == '/' || it == '*' || it == '|' || it == ':' }
                val host = (if (end < 0) rule else rule.substring(0, end)).lowercase()
                if (host.isEmpty() || !host.contains('.')) continue

                if (exception && parts.any { it in HIDE_EXCEPTION_OPTS }) { noGeneric += host; continue }
                if (parts.any { p -> SKIP_OPTIONS.any { p.startsWith(it) } }) continue
                val thirdOnly = parts.any { it == "third-party" || it == "3p" }

                val rest = (if (end < 0) "" else rule.substring(end)).trimEnd('^', '|')
                if (rest.isEmpty()) {
                    if (exception) allow += host
                    else block[host] = (block[host] ?: 0) or (if (thirdOnly) THIRD else ANY)
                    count++
                } else if (!exception && rest.startsWith("/") && !rest.contains('*') && !rest.contains('^')) {
                    pathBlock.getOrPut(host) { mutableListOf() }.add(rest)
                    count++
                }
            }

            val css = StringBuilder(generic.size * 40)
            generic.forEach { css.append(it).append(HIDE) }
            return FilterEngine(block, allow, pathBlock, css.toString(), site, noGeneric, count)
        }
    }
}
