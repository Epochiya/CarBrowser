# Car Browser

A private browser on your Android Auto screen, controlled from your phone like Remote Mouse.
Video pauses on the car screen whenever the car is moving (audio keeps playing).

## Download

Every push to `main` builds a new APK on GitHub Actions. Get it from **Releases → latest → CarBrowser.apk**.
Builds are signed with the same key, so new versions install over old ones.

## Car screen (Helium-style)

- Slim dark toolbar: back, forward, reload, address pill (lock + domain + page title), shields counter, tab counter, home.
- Optional tab strip, accent loading line, calm start page with clock, search and bookmark tiles.
- Tabs: links that open in a new window open as a new tab; tabs are restored next time you connect.
- Theater mode blows the video (or embedded player) up to fill the screen.

## Phone remote

- **Mouse:** big touchpad (tap, double-tap, two-finger scroll, two-finger tap = right click, double-tap-hold = drag),
  left/right click buttons, scroll wheel strip, live keyboard with arrow/Tab/Enter keys, quick bar.
- **Media:** timeline with seeking, play/pause, ±seek, skip intro, speed, mute, theater, phone volume.
- **Tabs:** open tabs, bookmarks, history.
- **Settings:** accent colour, pure black, car toolbar/tab strip, car text size, cursor size, pointer speed,
  acceleration, scroll speed, natural scrolling, tap-to-click, vibration, seek step, skip-intro length,
  search engine, home page, desktop sites, history, shields, cookies, wipe-on-disconnect, filter lists.

## Privacy

Shields (EasyList, EasyPrivacy, uBlock lists, Peter Lowe, cookie-banner lists), redirect guard, HTTPS-only,
cookies refused by default, DNT + GPC, no Safe Browsing lookups or WebView metrics, generic user agent,
site data wiped on disconnect.

## Android Auto setup

1. Android Auto settings → tap **Version** 10 times → developer mode.
2. ⋮ → **Developer settings** → enable **Unknown sources**.
3. Force-stop Android Auto, reconnect, and tick Car Browser in **Customise launcher**.
