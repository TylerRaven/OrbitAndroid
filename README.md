# Orbit for Android

The first Android version of Orbit, the macOS browser in `~/Documents/MangoBrowser`. It's a native
Kotlin + Jetpack Compose app built on Android System WebView. This version covers the core browser
only.

- Package: `com.mangostudios.orbit`
- minSdk 26 (Android 8.0), target/compile SDK 36
- Gradle 8.13 (wrapper), AGP 8.13.0, Kotlin 2.2.20, Compose BOM 2025.06.01, androidx.webkit 1.14.0

## Build

You need JDK 17. This Mac has Homebrew's `openjdk@17` (`/usr/bin/java` is only the macOS stub, so set
`JAVA_HOME`):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
```

The APK ends up at `app/build/outputs/apk/debug/app-debug.apk`. `local.properties` (not in git)
points at the SDK in `~/Library/Android/sdk`.

## Run

On the emulator (the existing `MopedDash` AVD, API 36). Use `-gpu host`; the default SwiftShader
renderer is far too slow at that AVD's resolution:

```bash
~/Library/Android/sdk/emulator/emulator -avd MopedDash -gpu host &
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.mangostudios.orbit/.MainActivity
```

On a phone, turn on USB debugging and use the same `adb install` command.

## Layout

| File | What it does |
| --- | --- |
| `Hue.kt` | Page colors: `site()`/`hash()` port `WallpaperMood.siteHue`, and `fromCss()` ports `Tab.hue(fromCSS:)` (greys return null). Private hue is 0.76 and the accent is blue (0.61). |
| `Glow.kt` | `PageGlow` is the Android version of `PageGlowPulse(fadesDown: true)`: a bloom and rim along the top edge that fade out by halfway down the sides. It rises over 0.35s, eases back over 0.3s, then fades over 2.2s. |
| `Browser.kt` | `Tab` (state, WebView, theme-color read, glow trigger) and `Browser` (tab list, session save, private profiles, URL-or-search parsing). |
| `BrowserScreen.kt` | The main screen: page card, progress line, ambient backdrop, glass address bar and toolbar. |
| `StartPage.kt` | New-tab page: aurora in the accent (violet for private), clock and greeting, search field, quick links. |
| `TabSwitcher.kt` | Grid of tab cards with thumbnails, close buttons, and New tab / Private / Done. |

## Done

- Tabs: open, close and switch, plus a switcher grid with page thumbnails. `target=_blank` and
  `window.open` (when the user tapped something) open a new tab beside the current one.
- Address bar: takes URLs, bare domains (adds `https://`) or Google searches. Back, forward, reload
  and stop, plus a loading line in the page's color. System back goes back in the page, then to
  the start page, and closes the tab switcher.
- Start page in Orbit's style.
- Glassy translucent chrome, rounded page card, accent color, and an ambient backdrop tinted with
  the page's color.
- Page-color glow. The hue comes from `<meta name="theme-color">`, else a known-site color, else
  one derived from the domain (same hash as the Mac, so the colors match). When a page finishes
  loading, the glow comes up along the top edge only, fades down the sides and fades out over
  about 2.5s. Double "finished" callbacks are debounced so each load glows once.
- Private tabs: violet glow, violet chrome and a violet card in the switcher. They get their own
  WebView profile (cookies and storage), via `androidx.webkit` MULTI_PROFILE, which needs WebView
  110 or newer. The profile is deleted when the last private tab closes, and leftover profiles are
  cleaned up at launch. Private tabs are never saved to the session or allowed third-party
  cookies. On older WebViews they fall back to no-cache, and the start page says cookies may be
  shared with normal tabs.
- Normal tabs come back after a restart. Restored tabs load only when you open them.
- Opens `http`/`https` links from other apps, and handles `WEB_SEARCH`.

Smoke-tested on the API 36 emulator: loading github.com and wikipedia.org, the glow (blue-ish for
GitHub's theme-color, violet for private), opening a normal and a private tab, switching through
the switcher, closing a private tab, and system back.

## Left for later

- Out of scope for this version: Spaces, AI panel, reader mode, translation, passwords, music
  glow, wallpaper choices.
- Not done yet: downloads (`DownloadListener`), file upload (`onShowFileChooser`), fullscreen
  video (`onShowCustomView`), permission prompts (camera, mic, location), history and bookmarks,
  address-bar suggestions, a settings screen (search engine, accent, glow strength), editable
  quick links, favicons, the loading shimmer, swipe-to-close in the switcher, tablet and landscape
  polish, a light theme, and a release signing config.
