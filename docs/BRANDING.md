# Branding and icon assets

**Product name:** RealStreamer  
**Tagline:** Go live from anywhere.  
**Category:** Android-first IRL broadcast studio.

---

## 1. The icon

The supplied icon set is a **white camera aperture/shutter on magenta** (`#E91E63`). It reads as "lens/broadcast" at small sizes, has no text, and works with Android's adaptive-icon masking.

Source assets live in [`icons/`](../icons):

```
icons/
  android/
    play_store_512.png                 store listing / web badge
    res/mipmap-*/RealStreamer*.png     legacy + adaptive foreground/background/monochrome
    res/mipmap-anydpi-v26/RealStreamer.xml
  ios/                                 full iOS AppIcon set + Contents.json
  web/                                 favicon.ico, 192/512, maskable variants, README
```

The icon set was produced with iconkitchen (per `icons/ios/Contents.json`); use it according to that tool's terms. The original folder is kept intact as the design source.

## 2. Android integration

Android resource names must be lowercase, so the integrated files are renamed:

| Source name | App resource |
|---|---|
| `RealStreamer.xml` | `app/src/main/res/mipmap-anydpi-v26/realstreamer.xml` |
| `RealStreamer_foreground.png` | `mipmap-*/realstreamer_foreground.png` |
| `RealStreamer_background.png` | `mipmap-*/realstreamer_background.png` |
| `RealStreamer_monochrome.png` | `mipmap-*/realstreamer_monochrome.png` |
| `RealStreamer.png` | `mipmap-*/realstreamer.png` (legacy 48–192 px) |

The manifest uses:

```xml
android:icon="@mipmap/realstreamer"
android:roundIcon="@mipmap/realstreamer"
```

The in-app wordmark uses `app/src/main/res/drawable-nodpi/realstreamer_badge.png` (the 512 px store icon) at 36/46/30 dp in the studio header, welcome screen, and About section.

The **notification** small icon remains `drawable/ic_stat_stream.xml` (a simple neon microphone). Status-bar icons are silhouettes; the full-color aperture is unsuitable at 24 dp, and Android's alpha mask would turn the magenta square into a solid block.

## 3. UI palette

The UI uses a broadcast-console dark theme with a neon signal color. The magenta icon is the brand mark; it is not the UI accent.

| Token | Hex | Use |
|---|---|---|
| Ink | `#050806` | App background |
| InkRaised | `#080D0A` | Sheets |
| Panel | `#0D1510` | Cards, controls |
| Line | `#1C2B22` | Borders |
| Neon | `#39FF88` | Primary actions, live, accents |
| NeonDeep | `#12A85A` | Pressed/secondary |
| Text | `#F0FBF4` | Primary text |
| TextMuted | `#A6B8AC` | Secondary text |
| Danger | `#FF5E6E` | Errors, end stream |
| Warning | `#FFC857` | Retrying, cautions |
| Blue | `#6EA8FF` | Custom chat platform |
| Violet | `#B48CFF` | Twitch platform |
| Icon magenta | `#E91E63` | Launcher/icon/badge only |

A future pass could adopt magenta as a secondary accent if the brand direction changes; that would require revisiting the live/error semantics (magenta and red are adjacent in hue).

## 4. iOS, web, and future surfaces

- The iOS icon set is complete and ready for an Xcode asset catalog; it is not wired into a build because there is no iOS target.
- The web icons are ready for a PWA/manifest if a companion web dashboard is added. The included `icons/web/README.txt` shows the HTML/manifest snippets.
- Do not use the icon for anything other than this product.

## 5. Changing the name or icon

The user-facing name is:

- `app/src/main/res/values/strings.xml` → `app_name`
- The wordmark strings in `StudioScreen.kt`, `WelcomeScreen.kt`, `SettingsScreen.kt`
- Notification strings in `StreamingService.kt`

The package namespace remains `com.irlstream.app` and `com.irlstream.core`. Renaming it would require moving source trees, the manifest, Gradle `namespace`/`applicationId`, scripts, and docs; it is intentionally out of scope for the 1.0.0 rebrand.

To replace the icon, regenerate the set, copy the Android mipmaps into `app/src/main/res/` with lowercase names, update `mipmap-anydpi-v26/realstreamer.xml` references and the manifest, and replace `drawable-nodpi/realstreamer_badge.png`. Keep the full source set under `icons/` for the store and future platforms.
