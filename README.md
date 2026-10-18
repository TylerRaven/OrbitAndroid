# Orbit for Android

The Android version of Orbit, my macOS browser ([TylerRaven/Orbit](https://github.com/TylerRaven/Orbit)).
It's a native Kotlin + Jetpack Compose app built on Android System WebView. This version covers
the core browser only.

- Package: `com.mangostudios.orbit`
- Android 8.0 or newer (minSdk 26, target/compile SDK 36)
- Gradle 8.13 (wrapper), AGP 8.13.0, Kotlin 2.2.20, Compose BOM 2025.06.01, androidx.webkit 1.14.0

## Install on a phone

1. On the phone, open the [latest release](https://github.com/TylerRaven/OrbitAndroid/releases/latest)
   and tap **Orbit.apk** to download it. The direct link is
   `https://github.com/TylerRaven/OrbitAndroid/releases/latest/download/Orbit.apk`.
2. Open the downloaded file, from the download notification or the Files app.
3. Android will say it can't install apps from that source. Tap **Settings**, turn on **Allow from
   this source** for the app you downloaded with (Chrome or Files, say), then go back and tap
   **Install**. On Android 8 you'll find it under Settings → Apps → Special app access → Install
   unknown apps.
4. If Play Protect warns that it doesn't recognise the app, tap **More details → Install anyway**.
   That's expected for an app that isn't on the Play Store.

The release APK is a debug build, signed with a debug key. To update, install the new APK over the
old one. If Android refuses because the signature changed (a build from a different machine),
uninstall Orbit first.

## Build

You need JDK 17. This Mac has Homebrew's `openjdk@17` (`/usr/bin/java` is only the macOS stub, so set
`JAVA_HOME`):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
```

The APK ends up at `app/build/outputs/apk/debug/app-debug.apk`. `local.properties` (not in git)
points at the SDK in `~/Library/Android/sdk`.

## Run on the emulator or over USB

The existing `MopedDash` AVD is API 36. Use `-gpu host`; the default SwiftShader renderer is far
too slow at that AVD's resolution:

```bash
~/Library/Android/sdk/emulator/emulator -avd MopedDash -gpu host &
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.mangostudios.orbit/.MainActivity
```

On a phone with USB debugging on, the same `adb install` works.

## Layout

| File | What it does |
| --- | --- |
| `Hue.kt` | Page colors: `site()`/`hash()` port `WallpaperMood.siteHue`, and `fromCss()` ports `Tab.hue(fromCSS:)` (greys return null). Private hue is 0.76 and the accent is blue (0.61). |
| `Glow.kt` | `PageGlow` is the Android version of `PageGlowPulse(fadesDown: true)`: a bloom and rim along the top edge that fade out by halfway down the sides. It rises over 0.35s, eases back over 0.3s, then fades over 2.2s. |
| `Browser.kt` | `Tab` (state, WebView settings and clients, theme-color read, glow trigger) and `Browser` (tab list, session, private profiles, prompts, URL-or-search parsing). |
| `WebExtras.kt` | Downloads (DownloadManager), the external-app handoff rules, and the prompt and fullscreen types. |
| `BrowserScreen.kt` | The main screen: page card, progress line, ambient backdrop, glass address bar, toolbar, confirmation dialogs, fullscreen host, and the blocked-certificate page. |
| `StartPage.kt` | New-tab page: aurora in the accent (violet for private), clock and greeting, search field, quick links. |
| `TabSwitcher.kt` | Grid of tab cards with thumbnails, close buttons, and New tab / Private / Done. |
| `MainActivity.kt` | Edge-to-edge window, the file picker, the storage permission (Android 8–9 only), and links from other apps. |

## Done

- **Tabs:** open, close and switch, plus a switcher grid with page thumbnails. `target=_blank` and
  `window.open` (when the user tapped something) open a new tab beside the current one.
- **Address bar:** takes URLs, bare domains (adds `https://`) or Google searches. Back, forward,
  reload and stop, plus a loading line in the page's color. System back leaves fullscreen,
  closes the tab switcher, goes back in the page, then goes to the start page.
- **Start page** in Orbit's style.
- **Orbit's look:** glassy translucent chrome, rounded page card, accent color, and an ambient
  backdrop tinted with the page's color.
- **Page-color glow:** the hue comes from `<meta name="theme-color">`, else a known-site color,
  else one derived from the domain (same hash as the Mac, so the colors match). When a page
  finishes loading, the glow comes up along the top edge only, fades down the sides and fades out
  over about 2.5s. Double "finished" callbacks are debounced so each load glows once.
- **Private tabs:** violet glow, violet chrome and a violet card in the switcher. See the
  security section for how their data is kept apart.
- **Downloads:** you confirm each one (name, size, site), then the system DownloadManager saves it
  to Downloads with a progress and "Download complete" notification. It sends the page's cookies
  and user agent, so signed-in downloads work. On Android 8–9 it asks for storage permission
  first. `blob:` and `data:` downloads aren't supported yet and show a message.
- **File uploads:** `<input type="file">` opens the system picker, including multi-select and
  `accept` types. Cancelling tells the page that nothing was chosen.
- **Fullscreen video:** a page's fullscreen request (video or any element) goes edge to edge in
  immersive mode, with system bars hidden until you swipe them in. Back, or the page's own exit
  button, restores the normal view.
- Normal tabs come back after a restart. Restored tabs load only when you open them.
- Opens `http`/`https` links from other apps, and handles `WEB_SEARCH`.

## Security

What the WebView security pass checked, and what I changed:

| Check | Status |
| --- | --- |
| File and content access | **Changed.** `allowFileAccess`, `allowContentAccess`, `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` are all set to `false` explicitly. `allowFileAccess` defaulted to `true` below API 30. |
| JavaScript bridges | **Checked.** No `addJavascriptInterface` anywhere. The app only reads `theme-color` with a one-off `evaluateJavascript` that returns a string. |
| Mixed content | **Changed.** Set to `MIXED_CONTENT_NEVER_ALLOW`. |
| Safe Browsing | **Changed.** Turned on per WebView (`safeBrowsingEnabled = true`) and in the manifest (`android.webkit.WebView.EnableSafeBrowsing`). Hits show WebView's own warning page. |
| SSL errors | **Changed.** `onReceivedSslError` always calls `handler.cancel()`. Orbit shows a "Not a secure connection" page with a warning icon in the address bar, and there's no way to proceed. |
| `intent://` and other schemes | **Changed.** It used to fire `startActivity` straight away. Now: <br>• `mailto:`, `tel:`, `market:`, `intent://` and other app links only go out after an **"Open in <app>?"** dialog, and only from a user tap or a redirect. Pages can't pop the dialog on their own. <br>• `intent://` URIs lose their explicit component, selector and permission-grant flags, and are forced to `CATEGORY_BROWSABLE`. A `browser_fallback_url` loads in Orbit when no app matches, but only if it's http(s). <br>• `javascript:`, `file:`, `content:`, `data:`, `blob:`, `chrome:` and `view-source:` navigations from pages are dropped. <br>• The address bar only loads `http(s)` and `about:blank` as typed; anything else becomes a search. |
| Private tabs | **Checked, then hardened.** Each private session uses its own WebView profile, via `androidx.webkit` MULTI_PROFILE (WebView 110+), so cookies, storage and cache are separate. The profile is deleted when the last private tab closes, and leftovers are swept at launch. Private tabs are never saved to the session and don't allow third-party cookies. **Added:** on WebViews without profiles, private tabs use no cache and clear cache, form data and history when closed. Cookies are still shared there, and the private start page says so. Downloads from private tabs go to the public Downloads folder, and the download dialog says that. |
| Cleartext traffic | **Changed.** It wasn't enabled before, but now it's explicit: `usesCleartextTraffic="false"` plus a network security config that blocks cleartext everywhere except `localhost`, `127.0.0.1` and `10.0.2.2` (the emulator's view of the Mac), for local development. Top-level `http://` links and typed `http://` addresses are upgraded to `https://` (HTTPS-first). Sites with no HTTPS at all won't load. |
| Also | Geolocation is off, and camera/mic permission requests are denied (the default). Backups are off (`allowBackup="false"` plus data-extraction rules), so open tabs and site data never go into cloud backups. `VIEW` intents from other apps are only accepted for `http(s)` URLs. |

Smoke-tested on the API 36 emulator with a local test page and real sites:
- github.com and wikipedia.org load, with the glow and the private-tab violet.
- Tabs open, switch (through the switcher) and close, including private tabs.
- Downloads: dialog, then notification, then the file in `/sdcard/Download`.
- File upload: the page got the chosen file's name and size.
- Fullscreen: immersive mode, and back restores the page.
- `mailto:` shows "Open in Gmail?" and Cancel leaves the page.
- An `intent://` link with no matching app loads its https fallback.
- `expired.badssl.com` is blocked.

## Left for later

- Out of scope for this version: Spaces, AI panel, reader mode, translation, passwords, music
  glow, wallpaper choices.
- Not done yet:
  - `blob:`/`data:` downloads, and a downloads list inside the app
  - camera capture in the file picker
  - permission prompts (camera, mic, location)
  - an "open over plain HTTP anyway" option for HTTP-only sites
  - history and bookmarks, and address-bar suggestions
  - a settings screen (search engine, accent, glow strength)
  - editable quick links and favicons
  - the loading shimmer, and swipe-to-close in the switcher
  - tablet and landscape polish, and a light theme
  - a release signing config (the published APK is a debug build)
