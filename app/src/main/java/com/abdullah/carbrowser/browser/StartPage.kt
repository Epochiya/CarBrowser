package com.abdullah.carbrowser.browser

import android.text.Html

/** Helium-style new tab page: calm, dark, a search pill and your bookmarks as tiles. */
object StartPage {

    private fun esc(s: String) = Html.escapeHtml(s)
    private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    fun html(): String {
        val accent = hex(Prefs.accent)
        val bg = if (Prefs.amoled) "#000000" else "#161616"
        val engine = Prefs.engine
        val action = engine.template.substringBefore('?')
        val param = engine.template.substringAfter('?').substringBefore('=')
        val tiles = Library.bookmarks.take(12).joinToString("") { b ->
            val host = Library.hostOf(b.url)
            val letter = esc(host.firstOrNull()?.uppercase() ?: "•")
            """<a class="tile" href="${esc(b.url)}"><span class="ic">$letter</span><span class="t">${esc(b.title.ifBlank { host })}</span></a>"""
        }
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>New tab</title>
<style>
:root{--a:$accent;--bg:$bg;--card:#232323;--line:#2e2e2e;--fg:#e8e8e8;--dim:#8d8d8d}
*{box-sizing:border-box;margin:0}
html,body{height:100%;background:var(--bg);color:var(--fg);font-family:system-ui,-apple-system,Roboto,sans-serif}
main{min-height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:4vh 6vw;gap:4vh}
.clock{font-size:clamp(40px,9vh,84px);font-weight:300;letter-spacing:-1px}
.date{color:var(--dim);font-size:clamp(13px,2.4vh,18px);margin-top:4px;text-align:center}
form{width:min(640px,90vw)}
input{width:100%;height:clamp(44px,8vh,60px);border-radius:999px;border:1px solid var(--line);background:var(--card);
 color:var(--fg);font-size:clamp(15px,2.6vh,20px);padding:0 24px;outline:none}
input:focus{border-color:var(--a)}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(110px,1fr));gap:12px;width:min(760px,92vw)}
.tile{display:flex;flex-direction:column;align-items:center;gap:8px;padding:14px 8px;border-radius:14px;
 text-decoration:none;color:var(--fg);background:transparent;transition:background .15s}
.tile:hover,.tile:active{background:var(--card)}
.ic{width:52px;height:52px;border-radius:14px;display:grid;place-items:center;background:var(--card);
 border:1px solid var(--line);color:var(--a);font-weight:600;font-size:22px}
.t{font-size:13px;color:var(--dim);max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
</style></head><body><main>
<div><div class="clock" id="c"></div><div class="date" id="d"></div></div>
<form action="${esc(action)}" method="get"><input name="${esc(param)}" placeholder="Search ${esc(engine.label)} or type a URL" autocomplete="off"></form>
<div class="grid">$tiles</div>
</main><script>
function t(){var n=new Date();document.getElementById('c').textContent=n.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});
document.getElementById('d').textContent=n.toLocaleDateString([], {weekday:'long',month:'long',day:'numeric'});}
t();setInterval(t,10000);
</script></body></html>"""
    }
}
