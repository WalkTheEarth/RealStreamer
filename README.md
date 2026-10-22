<p align="center">
  <img src="icons/android/play_store_512.png" width="112" alt="RealStreamer icon"/>
</p>

<h1 align="center">RealStreamer</h1>

**An Android-first IRL broadcast studio.** One GPU-composited scene, one hardware encode, and independent RTMP/RTMPS outputs — plus a generic UVC camera pipeline and a streamer-only console that viewers never see.

![Design chart](New%20Project.png)

> The chart above is the core UX: the live camera feed is the broadcast (WYSIWYG), and the side column is for the streamer/camera operator — local-only chat, stats, dropped frames, and output health. RealStreamer implements that separation explicitly: **broadcast layers** are composited into the encoded video; **streamer-only panels** are rendered in the app's sidebar and never encoded.

---

## Status

This repository builds and ships a complete Android app:

| Check | Result |
|---|---|
| `./gradlew :core:test` | **Passing** — and the RTMP publisher is verified end-to-end by publishing into a real FFmpeg RTMP listener (valid H.264 + AAC recorded, 60 video frames decoded) |
| `./gradlew :app:assembleDebug` | **Passing** |
| `./gradlew :app:assembleRelease` | **Passing** (R8, unsigned APK) |
| `./gradlew :app:lintDebug` | **0 errors** |

**Important:** the machine that produced this build has no Android device and no KVM, so the camera, UVC, OpenGL compositor, and foreground-service paths are compiled and code-reviewed but were not exercised on hardware. [docs/TESTING.md](docs/TESTING.md) is a concrete on-device checklist for the first install. Everything else — the domain model, settings serialization, RTMP protocol, FLV muxing, H.264 helpers — is covered by JVM tests.

---

## Feature matrix

| Capability | Status |
|---|---|
| Multiple RTMP destinations from a single encode | ✅ |
| RTMPS / TLS ingest | ✅ (implemented; local end-to-end test uses plain RTMP) |
| Independent reconnect, backoff, queueing, and keyframe gating per destination | ✅ |
| Built-in Camera2 back/front camera, zoom, stabilization | ✅ |
| Generic UVC webcam support (USB Video Class, no vendor code) | ✅ |
| UVC hotplug, USB permission flow, fallback to built-in camera | ✅ |
| WYSIWYG GPU compositor (preview is the exact encoded frame) | ✅ |
| On-stream text, chat, stats, web source, image/logo layers | ✅ |
| Streamer-only console: chat, stats, outputs, audio meter | ✅ |
| Twitch chat (anonymous or token) + generic JSON WebSocket + demo chat | ✅ |
| Drag-to-move / resize overlay editor | ✅ |
| Foreground service + wake lock for screen-off streaming | ✅ |
| Settings persistence and per-destination stream key masking | ✅ |
| iOS build | ⏳ not built — [port guide](docs/IOS-PORT.md) |
| Local recording, adaptive bitrate, SRT | ❌ not in this version |

**No DJI-specific code.** The DJI Action 4 works because it presents a standard UVC interface in Webcam mode. Any UVC camera should behave the same way.

---

## Documentation

RealStreamer ships with operator and engineering documentation, not just this README:

| Document | What it covers |
|---|---|
| [docs/USER-GUIDE.md](docs/USER-GUIDE.md) | Install, every screen, destinations, camera/UVC, overlays, chat, settings reference, going live, troubleshooting |
| [docs/STREAMING-GUIDE.md](docs/STREAMING-GUIDE.md) | Upload math, encoding choices, RTMP/RTMPS keys, UVC action-camera rigs, pre-flight checklist, ingest error catalog |
| [docs/OVERLAYS.md](docs/OVERLAYS.md) | Every source type, tokens, coordinates/z-order, web capture modes, example scenes, OBS mapping |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Compositor, threading, encoders/timestamps, RTMP protocol, fan-out, console separation, failure modes |
| [docs/DEVELOPING.md](docs/DEVELOPING.md) | Toolchain, module map, build/test/release, adding sources/providers, debugging, conventions |
| [docs/TESTING.md](docs/TESTING.md) | Automated tests, local multi-destination test, full on-device checklist, performance targets |
| [docs/SECURITY-PRIVACY.md](docs/SECURITY-PRIVACY.md) | Data inventory, permissions, network behavior, threat model, key handling |
| [docs/IOS-PORT.md](docs/IOS-PORT.md) | What is portable, the AVFoundation/Metal/VideoToolbox mapping, background-capture limits |
| [docs/BRANDING.md](docs/BRANDING.md) | The icon set, Android integration, palette, asset usage |
| [docs/FAQ.md](docs/FAQ.md) | Short answers to the common questions |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Planned and explicitly out-of-scope work |
| [docs/adr/](docs/adr/) | Architecture decision records |
| [docs/README.md](docs/README.md) | Documentation index and reading paths |
| [CHANGELOG.md](CHANGELOG.md) | 1.0.0 release notes |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | Licenses and attribution |

> **Name note:** the product and launcher are **RealStreamer**. The Android package namespace remains `com.irlstream.app` (and `com.irlstream.core`) for continuity; it is not user-visible. See [docs/BRANDING.md](docs/BRANDING.md).

---

## Requirements

- Android 8.0+ (API 26), a device with a hardware H.264 encoder.
- USB OTG + USB host support for external/UVC cameras.
- For building: JDK 17+, Android SDK Platform 36 and Build-Tools 36.0.0, internet access for Gradle dependencies.
- The project pins AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.2.21 / Compose BOM 2025.10.01 — a known-good combination.

The local SDK path lives in `local.properties` (git-ignored). Set it to your SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

---

## Build

```bash
# 1) protocol/model tests (includes the FFmpeg RTMP integration test)
./gradlew :core:test

# 2) debug APK
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.irlstream.app.debug/com.irlstream.app.MainActivity

# 3) release build (unsigned; sign before installing)
./gradlew :app:assembleRelease
apksigner sign --ks your-release.jks \
  --out RealStreamer-1.0.0.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

The debug package is `com.irlstream.app.debug`; the release package is `com.irlstream.app`.

The vendored UVC AAR in `app/libs/` contains only `arm64-v8a` and `armeabi-v7a` native libraries (the x86 emulator ABIs were stripped). The APK is limited to those ABIs as well. If you want to run on an x86 emulator, restore the original AAR from upstream.

---

## Quick start

1. **Welcome → Set up outputs.** Paste a full ingest URL including the stream key.
   - Twitch: `rtmp://live.twitch.tv/app/<stream key>` (RTMPS also works: `rtmps://live.twitch.tv/app/<key>`).
   - YouTube: `rtmp://a.rtmp.youtube.com/live2/<stream key>`.
   - Kick: use the RTMPS ingest URL and key from Kick's stream settings.
   - Custom: any `rtmp://` or `rtmps://` URL; the stream name is everything after the app path.
   Add as many as you want. Each destination gets its own connection.
2. **Open the studio.** Grant camera and microphone access. The preview is the broadcast frame.
3. **Choose a camera.** Tap the camera chip above the preview. Pick the built-in back/front lens or a UVC device. Put an action camera in **Webcam / UVC mode** and it will appear in the list.
4. **Add overlays.** Open **Overlays → On stream**. Add text, chat, stats, a web source, or an image. Drag the box on the preview to move it; drag the bottom-right handle to resize.
5. **Add streamer-only panels.** **Overlays → Streamer only** adds chat, stats, outputs, and audio meter to the Studio sidebar. These are never encoded.
6. **Go live.** The big GO LIVE button starts the encoder, the foreground service, and every enabled destination. The notification has an **End stream** action. You can turn the screen off and keep streaming.

---

## How it works (short version)

```
 Camera2 ─┐                                              ┌─> RtmpPublisher #1
          ├─> GL compositor scene (FBO) ─> preview        ├─> RtmpPublisher #2
 UVC ─────┘        │                (SurfaceView)        ├─> RtmpPublisher #3
   + overlays      └─> MediaCodec H.264 (surface input) ─┤
 AudioRecord ─────────> MediaCodec AAC-LC ───────────────┘
                          │
                          └── byte counters / dropped frames / chat ─> streamer console
```

- **One encode, N muxers.** The encoded H.264 access units and AAC frames are shared. Each destination has its own thread, bounded queue, connection, reconnect policy, and keyframe gate. A slow or dead service cannot stall the others.
- **The preview is not a separate render.** The compositor renders the scene to an offscreen texture once and blits it to both the preview SurfaceView and the encoder's input surface.
- **Overlays are real layers**, not screen capture. Text/chat/stats are Canvas-rendered bitmaps uploaded as GL textures; web sources are offscreen WebViews; images are uploads. The operator sidebar is Compose UI outside the compositor.
- **RTMP is implemented in-house** in `core`: handshake, chunking, AMF0, FLV/AVC/AAC tagging, acknowledgements, ping/pong, chunk-size negotiation, extended timestamps, TLS. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- **Timestamps are rebased per connection.** Reconnects restart near 0 ms instead of resuming hours into the timeline — important for ingest servers that treat each publish as a fresh stream.
- **The source of truth for the scene is a serializable model** (`AppSettings`, `Scene`, `SceneSource`, `SourceConfig`) in the pure-JVM `:core` module. The UI, persistence, and compositor all consume the same model.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## UVC notes (action cameras, webcams)

- UVC is **generic USB Video Class** — there are no camera-specific drivers or command hacks.
- Put the camera in its USB webcam mode, connect it over OTG, and grant the USB permission dialog. The device appears in **Camera source** and **Settings → Camera**.
- RealStreamer requests MJPEG or YUYV preview and falls back across supported sizes/formats. 1080p UVC cameras normally prefer MJPEG.
- If a UVC camera is unplugged while live, RealStreamer falls back to the built-in camera when the fallback switch is on. The stream keeps running.
- Audio is captured from the **phone microphone**. The included UVC library does not expose UVC/UAC audio; use the phone's mic or an external mic connected to the phone.

The DJI Action 4 is the reference device: in Webcam mode it enumerates as a standard UVC source, so it needs no special code.

---

## Chat

- **Twitch**: enter a channel. Read-only anonymous IRC is used by default. For channels/features that require auth, add an OAuth token and the account name.
- **Custom WebSocket**: point at a relay that emits JSON. Field paths are configurable (dot paths such as `data.user`). Plain-text messages are also accepted.
- **Demo messages** generate local sample chat for positioning overlays without a live channel.

Chat feeds both the on-stream chat overlay and the streamer-only chat panel. The per-layer "hide command messages" option only affects the broadcast overlay; the operator console still sees everything.

---

## Web sources

Web sources render an offscreen `WebView` into the compositor.

- **Opaque pages** use a virtual display + `Presentation` for smooth, full-rate rendering.
- **Transparent overlays** use a snapshot loop (~8 fps) so alpha is preserved. This is the default because most widgets are transparent.
- Pages that block embedding via DRM, pop-ups, or special user agents may not render. If the platform refuses a virtual display, the app falls back to snapshot mode and reports a warning in the layer editor.
- Network access follows the normal WebView rules; `usesCleartextTraffic` is enabled so http widget URLs can load.

---

## Streamer console

The side column in the studio is drawn by Compose, **not** by the compositor. Configure it in **Overlays → Streamer only**:

- **Chat** — live messages with platform colors.
- **Stream stats** — encoder FPS, video bitrate, dropped frames, uptime, output count, and a bitrate sparkline.
- **Output status** — per-destination connection state, bitrate, retries, and errors.
- **Audio meter** — live VU meter and mute control.

The console visibility and width are in **Settings → Streamer console**. Hiding the console gives the preview the full width.

---

## Project layout

```
core/                         Pure Kotlin/JVM (also the iOS-portable layer)
  model/                      AppSettings, Scene, sources, destinations (serializable)
  rtmp/                       RTMP client, AMF0, FLV, H.264/AAC helpers
  src/test/                   URL/AMF/FLV/H.264 tests + real FFmpeg RTMP integration test

app/                          Android app
  engine/
    StreamEngine.kt           Orchestrator: settings, camera, chat, live lifecycle
    VideoCompositor.kt        EGL/GLES scene, preview + encoder outputs, web textures
    OverlayRenderer.kt        Canvas rendering of text/chat/stats layers
    Camera2Controller.kt      Built-in camera
    UvcController.kt          Generic UVC + USB permission/hotplug
    VideoEncoder.kt           MediaCodec H.264, SPS/PPS extraction
    AudioEngine.kt            AudioRecord + AAC-LC
    StreamPipeline.kt         Multi-destination fan-out and per-output workers
    WebOverlayManager.kt      Virtual display + snapshot fallback
    StreamingService.kt       Foreground service / notification / wake lock
  chat/                       Twitch IRC + generic WebSocket
  data/                       Settings persistence
  ui/                         Compose UI (studio, destinations, overlays, settings, welcome)
  libs/AndroidUSBCamera-*.aar Vendored generic UVC engine (Apache-2.0)

docs/                         User/streaming/overlays/architecture/testing/security/iOS/FAQ/ADRs
icons/                        Icon source set (Android adaptive/legacy, iOS, web)
scripts/                      Local RTMP test helpers
CHANGELOG.md                  Release notes
CONTRIBUTING.md               Contribution rules
THIRD_PARTY_NOTICES.md        Licenses
```

---

## Testing

```bash
./gradlew :core:test          # unit + integration (FFmpeg required for the integration test)
./gradlew :app:lintDebug
```

The integration test creates a short H.264/AAC file with FFmpeg, publishes it with our RTMP publisher to a real FFmpeg RTMP listener, and validates the server's recording with `ffprobe`. It is skipped automatically if FFmpeg is unavailable.

For multi-destination testing on a LAN:

```bash
./scripts/local-rtmp.sh 19350 /tmp/out-a.flv
./scripts/local-rtmp.sh 19351 /tmp/out-b.flv
# In RealStreamer, add:
#   rtmp://<your-computer-LAN-IP>:19350/live/test
#   rtmp://<your-computer-LAN-IP>:19351/live/test
```

Then check both files with `ffplay`. Kill one listener while live and watch the other keep going; restart it and watch the auto-reconnect recover.

The complete device checklist is in [docs/TESTING.md](docs/TESTING.md).

---

## Known limitations / caveats

- **No iOS build.** The engine boundary is documented and the shared model/RTMP code is portable; see [docs/IOS-PORT.md](docs/IOS-PORT.md).
- **No local recording, SRT, or adaptive bitrate.** These are deliberate omissions for this version.
- **Web capture is device-dependent.** Virtual displays may be restricted by some ROMs; the fallback is lower frame rate and may not capture video/canvas content.
- **Front-camera orientation** uses the standard Camera2 mirrored rotation formula but should be verified per device. UVC cameras are not rotated (they normally deliver upright frames).
- **The activity locks to the stream orientation** (landscape or portrait) so the preview stays WYSIWYG. Changing format in Settings re-locks it.
- **Stream keys and chat tokens are stored in app-private storage, unencrypted.** They are masked in the UI, excluded from cloud backup/device transfer, and never logged. Treat a rooted device or a backup extraction as a compromise.
- **One profile.** Scene, destinations, and settings are a single profile.
- **Hardware encoder required.** Emulators and a few unusual devices may not provide a usable H.264 surface encoder.

---

## Troubleshooting

- **“Server refused publish” / 401**: the full URL must include the stream key; Twitch/YouTube keys rotate.
- **Destination stuck in RECONNECTING**: check the outbound network, the ingest URL, and whether the key is current. Hover/tap the destination card/console row for the server's error. The watchdog drops connections that stop accepting writes for 15 s.
- **No UVC camera listed**: the camera must be in UVC/webcam mode. Some USB-C hubs and cables are charge-only. Grant the USB permission dialog. Use **Refresh** in the camera sheet.
- **UVC opens then falls back to built-in**: the camera rejected the requested format or was unplugged. Try a different cable/hub; 1080p MJPEG is the usual mode.
- **Black preview**: check camera permission; check logcat tags `VideoCompositor`, `Camera2Controller`, `UvcController`. The compositor reports EGL failures through the snackbar.
- **Streaming stops when the screen turns off**: the foreground service must have started. Grant notification permission on Android 13+, and start the stream from the Studio so Android accepts the camera/mic foreground service types.
- **Chat empty**: Twitch anonymous read uses `justinfan`; a token may be required for restricted channels. Check the status line in Settings → Chat.

Log tags: `StreamEngine`, `VideoCompositor`, `VideoEncoder`, `AudioEngine`, `Camera2Controller`, `UvcController`, `WebOverlayManager`, `StreamingService`.

---

## Privacy

RealStreamer has no analytics and no backend. It connects only to the RTMP/RTMPS destinations, chat endpoints, and web URLs you configure. Stream keys and tokens remain in app-private storage and are excluded from Android backup and device transfer. See [docs/ARCHITECTURE.md#privacy-and-storage](docs/ARCHITECTURE.md#privacy-and-storage).

---

## Third-party components

The UVC engine is a vendored build of [AndroidUSBCamera](https://github.com/jiangdongguo/AndroidUSBCamera) (Apache-2.0), which bundles libuvc (BSD), libusb (LGPL-2.1), libjpeg-turbo (BSD/IJG), and saki's UVCCamera native layer (Apache-2.0). OkHttp, AndroidX, Jetpack Compose, and Kotlin/Kotlinx are Apache-2.0. Full notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

The RTMP client, compositor, encoders, overlay system, and UI in this repository are original to this project.
