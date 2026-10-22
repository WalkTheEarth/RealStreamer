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

## 3. UI palette and typography

The UI is a **broadcast deck**: near-black instrument panels, warm paper text, acid signal green for live paths and primary actions, and the icon's magenta reserved for ON AIR / record / END. Flat fills, one-pixel rules, clipped top-right corners, square tally lamps, and monospaced data — no rounded chat-app cards or gradient glows.

| Token | Hex | Use |
|---|---|---|
| Ink | `#070809` | App background |
| InkRaised | `#0B0D0F` | Sheets, rails, console |
| Panel | `#101315` | Cards, controls |
| PanelHigh | `#171B1E` | Insets, active segments |
| Line | `#23282B` | Hairlines and borders |
| LineStrong | `#394246` | Emphasized borders |
| Signal | `#B9FF3B` | Ready/live signal path, primary actions, active controls |
| SignalDeep | `#6C9E1D` | Pressed/secondary signal |
| Live | `#FF2E63` | ON AIR tally, record, END STREAM |
| Text | `#F2EFE6` | Warm paper text |
| TextSecondary | `#B7B6AC` | Secondary text |
| TextMuted | `#7B807C` | Muted text |
| TextFaint | `#54595B` | Captions, disabled |
| Danger | `#FF5449` | Errors, destructive text |
| Warning | `#FFC24B` | Retrying, local-only, cautions |
| Blue | `#7BA7FF` | Custom chat platform |
| Violet | `#C49BFF` | Twitch platform |
| Icon magenta | `#E91E63` | Launcher/icon/badge source |

`Live` is deliberately a slightly hotter magenta than the launcher icon's `#E91E63` so it reads as a tally lamp against the Ink background. Signal green and live magenta have separate jobs: green means the signal path is healthy/ready; magenta means the encoder is on air or the stream is ending. Do not use magenta for ordinary buttons or green for recording.

### Type

| Family | Weights | Use |
|---|---|---|
| Barlow Condensed | SemiBold, Bold | Display, screen titles, section headers, buttons, micro-labels |
| Barlow | Regular, Medium, SemiBold | Body, settings rows, chat |
| IBM Plex Mono | Regular, Medium | Timecodes, bitrate, resolution, camera IDs, counters |

All three are licensed under the SIL Open Font License 1.1. Font files live in `app/src/main/res/font/`; license texts are in [`licenses/OFL-Barlow.txt`](../licenses/OFL-Barlow.txt) and [`licenses/OFL-IBMPlexMono.txt`](../licenses/OFL-IBMPlexMono.txt). Condensed labels are uppercase with wide tracking; data is mono so digits do not jitter as they update.

### Layout rules

- Panels and buttons clip the **top-right** corner; the bottom-left stays square.
- Section headers use a two-digit frame index (`01`, `02`) and a hairline rule.
- Status is a square LED, not a round dot; the ON AIR plate has a tally bar, a lamp, and a timecode.
- The left edge carries the icon magenta as a 2 dp registration stripe on non-studio screens.
- Avoid pills, glows, large corner radii, and decorative gradients.

## 4. iOS, web, and future surfaces

- The iOS icon set is complete and ready for an Xcode asset catalog; it is not wired into a build because there is no iOS target.
- The web icons are ready for a PWA/manifest if a companion web dashboard is added. The included `icons/web/README.txt` shows the HTML/manifest snippets.
- Do not use the icon for anything other than this product.

## 5. Changing the name or icon

The user-facing name is:

- `app/src/main/res/values/strings.xml` → `app_name`
- The wordmark strings in `StudioScreen.kt`, `WelcomeScreen.kt`, `SettingsScreen.kt`
- Notification strings in `StreamingService.kt`

The package namespace remains `com.irlstream.app` and `com.irlstream.core`. Renaming it would require moving source trees, the manifest, Gradle `namespace`/`applicationId`, scripts, and docs; it is intentionally out of scope for the 1.0.x rebrand.

To replace the icon, regenerate the set, copy the Android mipmaps into `app/src/main/res/` with lowercase names, update `mipmap-anydpi-v26/realstreamer.xml` references and the manifest, and replace `drawable-nodpi/realstreamer_badge.png`. Keep the full source set under `icons/` for the store and future platforms.
