# Car Browser

A private browser on your Android Auto screen, controlled from your phone like Remote Mouse.
Video pauses on the car screen whenever the car is moving (audio keeps playing).

## Download

Every push to `main` builds a new APK on GitHub Actions. Get it from **Releases → latest → CarBrowser.apk**.
Builds are signed with the same key, so new versions install over old ones.

## Car screen

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

Android Auto hides navigation-type apps unless they were installed from Google Play, even with Unknown sources on. So Car Browser has to be installed in a way that marks it as coming from Play. Tapping the APK isn't enough.

### Needed

- [Shizuku](https://github.com/RikkaApps/Shizuku) ([Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)) gives approved apps adb-level access, without root or a PC.
- [UnknownAuto](https://github.com/GitMag/UnknownAuto) uses Shizuku to install an APK as if it came from Play.
- A Wi-Fi network for a few minutes (no internet needed). Shizuku uses Wireless debugging, which only works while the phone is on Wi-Fi.

### One-time setup

1. Android Auto settings → tap **Version** 10 times → ⋮ → **Developer settings** → enable **Unknown sources**.
2. Phone Settings → About phone → Software information → tap **Build number** 7 times.
3. On Wi-Fi, turn on **Developer options → Wireless debugging**. In Shizuku, start pairing, then enter the code from **Pair device with pairing code**.
4. Tap **Start** in Shizuku. This step needs repeating after every reboot.

### Install / update

1. Download `CarBrowser.apk` from the [latest release](../../releases/latest).
2. In UnknownAuto: **Load APK…** → pick the APK → **Install**. Allow Shizuku access, then confirm the install.
3. Force-stop Android Auto, reconnect, and tick Car Browser in **Customise launcher**.

Install every update this way. Afterwards you can turn off Wireless debugging. Car Browser keeps working.

**With a PC instead:** `adb install -i com.android.vending CarBrowser.apk`

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku) by RikkaApps
- [UnknownAuto](https://github.com/GitMag/UnknownAuto) by GitMag
- Filter lists downloaded at runtime: [EasyList / EasyPrivacy](https://easylist.to), [uBlock Origin filters](https://github.com/uBlockOrigin/uAssets), [Peter Lowe's list](https://pgl.yoyo.org/adservers/), Fanboy's cookie lists
