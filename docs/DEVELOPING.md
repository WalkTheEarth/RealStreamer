# Developing RealStreamer

Everything needed to build, test, debug, and extend the project.

---

## 1. Toolchain

| Tool | Version used | Notes |
|---|---|---|
| JDK | 21 | Java 17 bytecode target |
| Gradle | 8.14.3 | Wrapper committed; `./gradlew` |
| Android Gradle Plugin | 8.13.2 | Pinned, known-good |
| Kotlin | 2.2.21 | Compose compiler plugin matches |
| Compose BOM | 2025.10.01 | Material 3 |
| Android SDK | Platform 36, Build-Tools 36.0.0 | `compileSdk=36`, `targetSdk=36`, `minSdk=26` |
| Kotlinx Serialization | 1.9.0 | Core models |
| OkHttp | 4.12.0 | Chat WebSockets |
| JUnit | 4.13.2 | JVM tests |

Newer versions almost certainly work, but upgrading AGP/Kotlin together is recommended. Kotlin 2.x requires the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) at the same version; it is already configured.

### SDK setup

Create `local.properties` at the repository root:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

The repository's development environment used a local `.tooling/sdk`; that directory and `local.properties` are git-ignored.

---

## 2. Build and test

```bash
./gradlew :core:test          # unit tests + FFmpeg RTMP integration test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
./gradlew clean               # after major resource/package changes
```

- The integration test is skipped when FFmpeg is not installed.
- `assembleRelease` produces an unsigned APK; R8 minification is on. Sign with `apksigner`.
- Debug installs as `com.irlstream.app.debug`; release as `com.irlstream.app`.
- Native ABIs are limited to `arm64-v8a` and `armeabi-v7a`; the vendored UVC AAR's x86 libs were removed.

### Release signing

```bash
keytool -genkey -v -keystore realstreamer.jks -keyalg RSA -keysize 2048 -validity 10000 -alias realstreamer
./gradlew :app:assembleRelease
apksigner sign --ks realstreamer.jks \
  --out RealStreamer-1.0.0.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Do not commit keystores or signing passwords.

---

## 3. Module map

```
:core  (Kotlin/JVM, no Android)
  com.irlstream.core.model
    Models.kt            AppSettings, Scene, SceneSource, SourceConfig, Destination, ...
    Sources              factories for default/broadcast/streamer sources
  com.irlstream.core.rtmp
    RtmpUrl.kt           URL parsing, masking, tcUrl/publish URL
    Amf0.kt              AMF0 value model + encoder/decoder
    Flv.kt               FLV tag builders + H264/AAC helpers
    RtmpPublisher.kt     TLS/handshake/chunking/commands/media/ACKs/watchdog support
  src/test               protocol, URL, AMF0, H.264, serialization tests

:app  (Android)
  com.irlstream.app
    RealStreamerApp.kt   Application; owns SettingsRepository + StreamEngine singleton
    MainActivity.kt      Edge-to-edge Compose host
    engine/
      EngineState.kt     StateFlow model for UI/telemetry
      StreamEngine.kt    Orchestrator: settings, preview, camera, live lifecycle, chat, images, web
      VideoCompositor.kt EGL/GLES2 scene, preview + encoder outputs, web OES layers
      OverlayRenderer.kt Canvas → Bitmap for text/chat/stats
      Camera2Controller.kt
      UvcController.kt
      VideoEncoder.kt
      AudioEngine.kt
      StreamPipeline.kt  DestinationWorker fan-out
      WebOverlayManager.kt
      StreamingService.kt
    chat/ChatManager.kt
    data/SettingsRepository.kt
    ui/                  Compose theme, components, screens, ViewModel
  libs/                  Vendored UVC AAR + license
```

Package names remain `com.irlstream.*` for continuity; the product is RealStreamer.

---

## 4. Engine contract and threading

| Class | Thread | Contract |
|---|---|---|
| `StreamEngine` | Main | Owns lifecycle. Public UI calls are main-thread-safe; heavy teardown runs on `Dispatchers.Default`. |
| `VideoCompositor` | `HandlerThread("irl-compositor")` | All GL calls, SurfaceTexture creation/update, layer texture uploads. Public methods post. |
| `VideoEncoder` | `Thread("video-encoder")` | MediaCodec output drain; callbacks on this thread. |
| `AudioEngine` | `Thread("audio-capture")` | AudioRecord read + AAC encode; callbacks on this thread. |
| `DestinationWorker` | One thread per destination | Queue drain, connect/publish/reconnect, stats. |
| RTMP reader/watchdog | Threads per publisher | Chunk parsing/acks/pings; stall detection. |
| `StreamingService` | Main | Foreground notification + wake lock; observes engine state. |

Rules:

- Never call GL methods or `SurfaceTexture.updateTexImage()` outside the compositor thread.
- Treat encoded byte arrays as immutable and share them across destinations.
- Keep UI work on the main thread; `_state.update` is thread-safe.
- A camera or compositor surface is owned by its producer (SurfaceView/MediaCodec); the engine borrows it. UVC is explicitly closed and waited for before compositor teardown.
- Long joins (pipeline, encoders) must not run on the main thread.

### Live lifecycle of note

- `goLive()` validates destinations + mic permission + compositor size, starts the pipeline, encoder, audio, sets LIVE, starts the FGS.
- `stopLive()` sets STOPPING, detaches the encoder surface, tears down off-main, then either pauses capture (if no preview is attached) or rebuilds the preview if video settings changed while live.
- While live, video/audio quality changes are stored as pending; scene/camera/chat/destination changes apply live.

---

## 5. Adding a new broadcast source type

1. **Model**
   - Add an enum value to `SourceType` in `Models.kt` (with `isStreamerOnly = false`).
   - Add a `@Serializable` subclass to `SourceConfig` with a `@SerialName("…")`.
   - Add a factory branch in `Sources.broadcast(type, order)` with a sensible default rect/config.
2. **Compositor**
   - Add a `when (source.type)` branch in `VideoCompositor.updateLayers()` (build/upload texture) and `drawLayers()` (draw).
   - Add a content key if the source is dynamic (see `textKey/chatKey/statsKey`).
   - Create textures with `GlTextures.create2d()` (bitmap) or `provideWebTexture` (producer-backed OES).
3. **Renderer** (if it is Canvas-based)
   - Add a function in `OverlayRenderer` that returns an ARGB `Bitmap` sized to the layer rect, scaling from a 1080p reference. Recycle it after upload if you own it.
4. **UI**
   - Add the icon/label/description to `OverlaysScreen` (`sourceIcon`, `broadcastDescription`, add-sheet list).
   - Add per-source properties in `SourceEditorSheet`.
5. **Persistence**
   - Extend `SettingsSerializationTest` to include the new source so the sealed discriminator round-trips.
6. **Docs**
   - Update `docs/OVERLAYS.md` and the feature matrix.

Avoid adding Android types to `core` models; keep them serializable and portable.

### Adding a streamer-only panel

Add a `SourceType`/`ConsolePanel` value, a factory in `Sources.streamerConsole`, a branch in `StreamEngine` console rendering (`StudioScreen.ConsoleContent`), and a Compose panel. The compositor must remain unaware of it.

---

## 6. Adding a chat provider

1. Add settings fields to `ChatSettings` (channel/URL/fields).
2. In `ChatManager`, add a `scope.launch(Dispatchers.IO) { providerLoop(config) }` in `restart()`.
3. Use a reconnect loop with a `suspendCancellableCoroutine` or a blocking client that can be cancelled; emit `ChatMessage` via `onMessage`.
4. Parse into `ChatMessage(id, author, text, colorArgb, platform, timestampMs)`; never render provider HTML.
5. Add the platform to `ChatPlatform` if it is a distinct source.
6. Add the fields to the Settings UI and `SettingsSerializationTest`.
7. Document auth scopes/tokens and store secrets the same way (private, masked, no logs).

Twitch IRC parsing is a good reference (`parseTwitchLine`, IRC tag unescaping, PING/PONG, reconnect).

---

## 7. Adding or changing encoding presets

- Video presets: `SettingsScreen.videoPresets` (label/size/fps). Keep widths/heights even; media encoders require even dimensions.
- Composable constraints: the compositor output = `VideoSettings.width × height`; the encoder must be created with the same size. `StreamEngine.goLive()` guards the compositor/encoder size match.
- If adding >1080p, verify `GL_MAX_TEXTURE_SIZE`, encoder level/profile, and thermal behavior; the current encoder profile/level fallback uses High/4.1 then defaults.
- Audio: `AudioSettings` (sample rate/channels/bitrate); AAC-LC supported rates. `AudioEngine` synthesizes a valid ASC if the encoder omits one.

---

## 8. Debugging

### Logcat

```bash
adb logcat -s StreamEngine VideoCompositor VideoEncoder AudioEngine Camera2Controller \
  UvcController WebOverlayManager StreamingService RtmpPublisher
```

The repo script wraps this: `./scripts/on-device-smoke.sh`.

### Local RTMP servers

```bash
./scripts/local-rtmp.sh 19350 /tmp/a.flv
./scripts/local-rtmp.sh 19351 /tmp/b.flv
ffplay /tmp/a.flv
```

Add both as destinations using the computer's LAN IP. This isolates compositor/encoder/RTMP issues from platform ingest issues.

### Core protocol test

```bash
./gradlew :core:test --tests '*RtmpPublisherIntegrationTest*' --info
```

It generates media with FFmpeg, publishes with our RTMP client, and validates the server's recording with ffprobe.

### Common development pitfalls

- **Preview black but no errors**: the SurfaceView's EGL surface may have failed to create; check `VideoCompositor` logs. The engine borrows the SurfaceView surface; do not release it in the engine.
- **UVC stale producer**: the engine calls `UvcController.closeAndWait()` before releasing the compositor or switching producers. Do not bypass that ordering.
- **Compositor size mismatch**: while live, `activeVideo()` (the live settings) wins over the edited `settings.video`; pending changes rebuild after stop.
- **Serialization changes**: adding a config subclass requires the `@SerialName` and a matching round-trip test; old JSON with missing fields uses defaults (ignoreUnknownKeys + defaults).
- **Resource names**: Android file-based resources must be lowercase `a-z0-9_`; the vendor icon set's `RealStreamer*` files were renamed to `realstreamer*`.
- **UVC ABIs**: only ARM libraries are packaged. An x86 emulator will not load them.

### Inspecting the APK

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E 'lib/|mipmap.*realstreamer'
aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head
```

---

## 9. Testing philosophy

- **Pure/JVM**: URL parsing, AMF0, FLV/H.264 helpers, settings serialization, RTMP against a real server.
- **Android compile/lint**: the full engine/UI must compile for debug and release (R8) with 0 lint errors.
- **Device**: camera/UVC/GL/FGS/WebView require the checklist in [TESTING.md](TESTING.md). Do not claim those features are verified without running it.
- When fixing a device bug, add the reproduction steps (device, ROM, camera, format) to the testing doc.

---

## 10. Code conventions

- Kotlin, 4-space indent, trailing commas where the file already uses them.
- Immutable models; `copy` for changes.
- Engine callbacks expose small data classes; UI state is a single `EngineState` StateFlow.
- Keep `:core` free of `android.*`.
- Do not log keys/tokens or full destination URLs; use `RtmpUrl.masked()`.
- Comments explain *why* (ordering, timestamp semantics, threading), not what the code obviously does.
- New user-visible strings live in Compose code; the app is English-only and `resourceConfigurations = ["en"]`.
- Prefer the existing components (`PrimaryButton`, `PanelCard`, `SheetContent`, `ChoiceChip`) over ad-hoc Material widgets.

---

## 11. Dependency upgrades

- Upgrade AGP and Gradle together; AGP 8.13 requires Gradle ≥ 8.13.
- Upgrade Kotlin and the Compose compiler plugin together; keep the Compose BOM in the same era.
- Compose Material icons-extended is large at compile time; it is used for the full icon set.
- `material-icons-*` 1.7.x emits deprecation warnings for auto-mirrored icons; functional.
- Re-run `lintDebug`, release R8, and the core integration test after any upgrade.
- Keep `local.properties` out of version control.

---

## 12. Known development gaps

- No instrumented tests (requires a device/emulator). The on-device checklist is the substitute.
- No screenshot tests; the UI can only be visually verified on hardware.
- The web capture path depends on ROM support for virtual displays and detached WebView rendering.
- The UVC library is vendored and stripped; updating it is a manual AAR replacement plus re-verification of the native ABI alignment and `UVCCamera` API.
- Package name `com.irlstream.*` is historical and intentionally unchanged.
