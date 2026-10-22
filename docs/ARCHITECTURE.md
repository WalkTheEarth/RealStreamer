# Architecture

RealStreamer is organized around three hard requirements:

1. **WYSIWYG** — the preview and the encoded video must be the same pixels. There is no screen capture and no second preview pipeline.
2. **One encode, many outputs** — adding a destination must not add another encoder, and a failed destination must not stall the others.
3. **Operator privacy** — the streamer console is app UI, never a composited scene layer.

---

## Module boundaries

```
:core (pure Kotlin/JVM)
  model/   AppSettings, VideoSettings, AudioSettings, CameraSettings, Destination,
           Scene, SceneSource, SourceConfig, ChatSettings
  rtmp/    RtmpUrl, Amf0, Flv, H264, Aac, RtmpPublisher
  tests    JVM unit tests + FFmpeg-backed RTMP integration test

:app (Android)
  engine/  capture, compositor, encoders, fan-out, service
  chat/    Twitch IRC + generic WebSocket
  data/    JSON settings persistence
  ui/      Jetpack Compose app
```

`:core` has no Android dependency. That keeps the protocol and model testable on the JVM and is the natural shared layer if the engine is ported to iOS/desktop.

---

## Data flow

```
                     ┌──────────────── Scene / OverlayContent ───────────────┐
Camera2  ── OES ─┐   │  broadcast layers only                                │
UVC      ── OES ─┼──▶│  GPU compositor (EGL + GLES2)                          │
WebView  ── OES ─┘   │    scene FBO at output resolution                      │
Bitmap layers ───┘   └───────┬───────────────────────────────┬───────────────┘
                             │ blit                          │ blit
                     preview TextureView            MediaCodec input surface
                                                     (H.264, hardware)
AudioRecord ── PCM ──▶ MediaCodec AAC-LC ── raw AAC ──┐
                                                      ▼
                                               StreamPipeline
                          ┌───────────────┬───────────┴───────────┐
                          ▼               ▼                       ▼
                    DestinationWorker DestinationWorker     DestinationWorker
                    RtmpPublisher     RtmpPublisher         RtmpPublisher
                          │               │                       │
                       Twitch           YouTube                 Kick/custom
```

The scene FBO is rendered once per frame and blitted to both outputs. The encoder receives the same frame the preview shows.

---

## The model (`:core`)

`AppSettings` is the single serializable source of truth:

```
AppSettings
├── video: VideoSettings          (resolution, fps, bitrate, keyframe interval)
├── audio: AudioSettings          (source, channels, bitrate, AEC/NS/AGC, mute)
├── camera: CameraSettings        (built-in lens / UVC, mirror, fallback)
├── destinations: [Destination]   (full ingest URL, enabled, auto-reconnect)
├── scene: Scene
│   ├── backgroundArgb
│   └── sources: [SceneSource]
│       ├── type: TEXT | CHAT | STATS | WEB | IMAGE |
│       │         STREAMER_CHAT | STREAMER_STATS | STREAMER_OUTPUTS | STREAMER_AUDIO
│       ├── audience: BROADCAST | STREAMER_ONLY
│       ├── rect: NormalizedRect  (0..1 coordinates)
│       ├── order: Int            (compositing order)
│       └── config: SourceConfig  (sealed: Text/Chat/Stats/Web/Image/Console)
├── chat: ChatSettings
└── console: ConsoleSettings
```

**Audience is explicit.** A `CHAT` source is an on-stream layer; a `STREAMER_CHAT` source is a sidebar panel. The compositor only walks `scene.broadcastSources`. The console only walks `scene.streamerSources`. This is how the design chart's operator column is kept off the wire.

Source configs are polymorphic (`@Serializable` sealed class with a discriminator). Rect coordinates are normalized so a scene survives resolution changes. Text supports `{time}`, `{uptime}`, `{fps}`, `{bitrate}`, `{resolution}`, `{destinations}` tokens when `dynamic` is enabled.

---

## Threading model

| Owner | Thread | Responsibility |
|---|---|---|
| `StreamEngine` | Main | Settings collection, lifecycle, camera open/close, live state, UI StateFlow |
| `VideoCompositor` | `HandlerThread("irl-compositor")` | EGL context, textures, scene draw, preview/encoder presentation |
| `VideoEncoder` | `Thread("video-encoder")` | MediaCodec output drain, AVCC conversion, config extraction |
| `AudioEngine` | `Thread("audio-capture")` | AudioRecord read, AAC encode, output drain, level metering |
| `DestinationWorker` (×N) | `Thread("rtmp-…")` | Connect, reconnect, queue drain, RTMP writes |
| RTMP reader (per publisher) | `Thread("rtmp-reader-…")` | Chunk parsing, ACKs, pings, server commands |
| RTMP watchdog (per destination) | `Thread("rtmp-watchdog")` | Closes stalled sockets (no write progress for 15 s) |
| `ChatManager` sources | OkHttp / IO coroutines | IRC/WebSocket parsing and reconnect |
| `StreamingService` | Main | Foreground notification, wake lock, stop action |

Rules of thumb:

- GL calls and `SurfaceTexture.updateTexImage()` only happen on the compositor thread.
- Encoded frames are immutable byte arrays; the pipeline is the only fan-out point.
- Bounded per-destination queues (512 packets) absorb network jitter. Non-key video and audio are dropped when a destination falls behind; keyframe gating prevents a late joiner from decoding garbage.
- Shutdown joins are done off the main thread (`stopLive()` transitions to `STOPPING` and tears down on `Dispatchers.Default`), so a multi-destination stop cannot ANR the UI.

---

## The compositor

`VideoCompositor` owns one EGL context, a pbuffer surface for offscreen drawing, and two window surfaces:

1. the preview `Surface` from a `TextureView`;
2. the `Surface` returned by `MediaCodec.createInputSurface()`.

Per frame it:

1. makes the pbuffer current and binds the scene FBO at the configured output size;
2. clears to the scene background;
3. draws the camera OES texture with a UV transform (`SurfaceTexture` matrix × rotation × mirror), cover-fitted into the canvas;
4. rebuilds any dirty overlay bitmaps (text/chat/stats are cached by a content key; images are uploaded on identity change);
5. draws broadcast layers in ascending `order`;
6. makes each output surface current, blits the FBO texture, and swaps;
7. for the encoder surface, calls `eglPresentationTimeANDROID` with the shared stream timestamp before the swap.

Camera and web frames arrive as **external OES textures**. Overlay text/chat/stats/images are **2D textures** uploaded from `Bitmap`s. Web sources are OES textures produced by offscreen WebViews (see below).

### Why TextureView

The preview is a `TextureView` inside a Compose `AndroidView`. A `SurfaceView` nested in that interop layer rendered black on a Samsung Galaxy S23 FE (Android 16) even while Camera2 delivered 30 fps and the compositor was swapping frames; the SurfaceView's window layer never resolved through the Compose render node. A TextureView draws as part of the normal window, so the compositor's EGL output is visible and Compose controls/handles can still sit above it. The cost is one GPU copy at preview resolution, which is negligible next to the 720p/1080p encode.

The preview `Surface` is still a normal EGL window surface; only the consumer view changed. The compositor renders the scene once into its FBO and blits it to the TextureView surface and the encoder surface. The blit normalizes against the actual preview surface dimensions, not the 1280x720 render target; using the FBO size here previously placed the preview in the lower-left quarter of the view.

### Rotation and fit

- The camera buffer is usually landscape. `Camera2Controller` computes `(sensorOrientation ∓ displayRotation)` (front cameras use the mirrored direction) from the **physical display rotation**. The compositor rotates UVs 0/90/180/270 and optionally mirrors U before applying the `SurfaceTexture` matrix.
- Do not confuse `context.display.rotation` with the physical display rotation. On a fixed-landscape activity the former can report 0 while the panel is rotated 90°, which makes a landscape sensor buffer look portrait and crops it hard. The controller and the display listener both use the `DisplayManager` physical rotation.
- The camera is cover-fitted: if the sensor aspect differs from the canvas, edges are cropped, never stretched.
- A `DisplayManager` listener updates the built-in camera's UV rotation when the device flips between the two landscape (or portrait) orientations, without restarting the camera. It uses the same rotation source as `Camera2Controller.open`; mixing sources caused a one-frame overwrite to a sideways transform after every camera open.
- The stream orientation is fixed by the video settings and the activity is locked to it for WYSIWYG framing.

### Overlay rendering

`OverlayRenderer` draws into ARGB bitmaps sized to each layer's pixel rect at the output resolution:

- **Text**: StaticLayout, configurable font size/weight/alignment/colors, centered vertically, with a rounded backdrop.
- **Chat**: panel + header + the newest messages bottom-aligned, colored names, optional timestamps, command filtering, max-message trimming to fit.
- **Stats**: compact label/value rows with a neon accent.
- Bitmaps are uploaded once per content change and recycled after upload. The compositor keys them by config + content + dimensions, so an idle scene costs no Canvas work.

All overlay sizes scale from the 1080p reference (`outputHeight / 1080`), so a 720p stream keeps the same visual proportions.

---

## Capture

### Built-in camera (`Camera2Controller`)

- Chooses the camera by lens facing; picks a preview size whose aspect matches the output and whose area is closest to the target.
- Opens a `TEMPLATE_RECORD` repeating request with continuous AF/AE/AWB, an FPS range, and video stabilization when the device supports it.
- Exposes zoom (crop region) and torch (flash mode).
- Preview frames are produced directly into the compositor's OES `SurfaceTexture`.

### UVC (`UvcController`)

- Enumerates USB devices whose interfaces are USB Video Class (class 14). No VID/PID tables.
- Uses the system USB permission dialog with a package-scoped, mutable `PendingIntent` and a dynamically registered receiver using `ContextCompat.RECEIVER_NOT_EXPORTED` (Android 14-safe).
- Uses `USBMonitor.openDevice` + `UVCCamera` from the vendored Apache-2.0 stack to open the camera, select a supported MJPEG/YUYV size, attach the preview to the compositor's `SurfaceTexture`, and start streaming.
- On failure it retries alternate formats/sizes, and the engine falls back to the built-in camera if configured. A single auto-open guard per device-attach prevents permission/fallback loops.
- Hotplug is handled by attach/detach broadcasts; detaching the active camera closes it and triggers fallback.

---

## Encoding and timestamps

Video and audio share one stream epoch (`SystemClock.elapsedRealtimeNanos()` at GO LIVE).

- **Video** — `VideoEncoder` configures a hardware `video/avc` encoder with a surface input, CBR when available, a 0-B-frame real-time configuration, and a keyframe interval. The encoder outputs access units; `H264.annexBToAvcc` converts start-code framing to 4-byte length prefixes. If `csd-0/csd-1` are missing, SPS/PPS are recovered from the first keyframe to build the AVCDecoderConfigurationRecord. Frames before the codec config is known are withheld.
- **Audio** — `AudioEngine` uses `AudioRecord` (VOICE_COMMUNICATION/CAMCORDER/MIC/VOICE_RECOGNITION) with optional AEC/NS/AGC, encodes AAC-LC, and forwards the 2-byte AudioSpecificConfig (or synthesizes a valid one).
- **Sync** — video PTS is the EGL presentation time since the epoch; audio PTS is derived from submitted sample counts. MediaCodec returns microsecond PTS; FLV/RTMP use milliseconds.

**Reconnect timestamp rebasing:** every new RTMP connection is a new server-side stream. A destination's first keyframe becomes timestamp 0, and all subsequent audio/video is offset from it. Without this, a reconnect 40 minutes in would begin at 2,400,000 ms.

---

## Multi-destination pipeline and RTMP

`StreamPipeline` holds a `DestinationWorker` per enabled destination. Each worker:

- waits for the video config;
- opens an `RtmpPublisher` and publishes the stream key;
- sends AVC/AAC sequence headers;
- requests a keyframe and drops audio/video until that keyframe arrives;
- sends media with rebased timestamps;
- reports LIVE state, bitrate (from bytes written), retry count, and drops to the engine;
- reconnects with exponential backoff (500 ms → 15 s) unless auto-reconnect is off;
- is watched by a thread that closes the socket if no write completes for 15 s.

The queue policy: at ~380 queued packets, non-key video is dropped; at ~420, audio is dropped; overflowing packets increment the drop counter. The encoder and other destinations are unaffected.

### The RTMP client (`:core/rtmp`)

Implemented from scratch (no librtmp/FFmpeg binary), which keeps it testable and gives us TLS and clean per-connection state:

- **Handshake**: C0/C1/C2, simple handshake.
- **Chunking**: fmt 0 writes; reads fmt 0–3 with per-chunk-stream state, interleaved chunks, chunk-size changes, and extended timestamps.
- **Commands**: AMF0 `connect`, `releaseStream`, `FCPublish`, `createStream`, `publish`; waits for `_result` and `NetStream.Publish.Start`.
- **Housekeeping**: Window Acknowledgement Size, acknowledgements, Set Peer Bandwidth, User Control Ping→Pong.
- **Media**: FLV tag bodies for AVC sequence headers, AVC NALU frames, AAC sequence headers, and raw AAC. H.264 access units are AVCC (length-prefixed).
- **TLS**: `rtmps://` uses an `SSLSocket` with HTTPS endpoint identification.
- **URL handling**: first path segment is the RTMP app; the rest is the stream name (including query parameters). Keys are masked in logs/UI.

The JVM integration test publishes a real H.264/AAC file into an FFmpeg RTMP listener and verifies the recorded output with ffprobe.

---

## Chat

`ChatManager` merges:

- **Twitch IRC over WebSocket** (`wss://irc-ws.chat.twitch.tv`): anonymous `justinfan` read-only by default, optional OAuth token + username, tag capability for display names/colors/ids, PING/PONG, reconnect.
- **Generic WebSocket**: JSON (with configurable dot-path fields) or plain text, reconnect.
- **Demo**: local sample messages for overlay positioning.

Messages are normalized to `ChatMessage(id, author, text, colorArgb, platform, timestamp)`, capped at 300 in the engine, and fanned to the console UI and the compositor's chat layers. The overlay filtering (`!commands`, max messages) is per-layer; the console is unfiltered.

---

## Web sources

`WebOverlayManager` keeps one capture per enabled broadcast web source:

- **Opaque**: `VirtualDisplay` + `Presentation` + `WebView`, produced into an OES `SurfaceTexture` owned by the compositor. Full-rate, GPU-composited.
- **Transparent** (default): a detached WebView is measured/laid out and drawn to a bitmap ~8 times per second, preserving alpha. This is lower fidelity but avoids opaque black backgrounds.

The manager releases the producer (`Presentation`/`VirtualDisplay`/`Surface`) before asking the compositor to release the `SurfaceTexture`, so the GL thread never tears down a texture that is still being produced into. URL typing is debounced in the engine (450 ms) to avoid restarting captures on every keystroke.

---

## Live lifecycle and the streamer console

- **Preview** is started when a `TextureView` attaches and camera permission is granted. In the foreground the camera remains open; on background without a live stream, the engine closes the camera, chat, web captures, and compositor.
- **GO LIVE** validates destinations and audio permission, requires the compositor output size to match the video settings, starts the pipeline, video encoder, audio encoder, and the foreground service, then transitions state to LIVE.
- **STOPPING** detaches the encoder surface immediately and tears down network/encoders on a background dispatcher; the camera preview continues.
- **Settings during a live stream**: scene, chat, camera source, and destinations apply live. Video/quality and audio-quality changes are stored, flagged as pending, and applied on the next stream. After a stop, a pending video change rebuilds the compositor so preview and encoder stay matched.
- **Background streaming**: the foreground service declares `camera|microphone`, holds a partial wake lock (12 h safety cap), exposes an **End stream** action, and updates the notification with duration and output health. The Activity can be destroyed; the engine and service keep the process alive.
- **Console**: `StudioScreen` composes `ConsoleColumn` / `ConsoleContent` from the enabled `STREAMER_ONLY` sources. The compositor knows nothing about it. This is the chart's side column.

---

## Persistence and privacy

`SettingsRepository` stores one JSON document in `filesDir/irlstream-settings.json`, debounced 300 ms, written via a temp file + rename. Corrupt files are moved aside and defaults are used.

- Stream keys and OAuth tokens are in that private file, unencrypted. They are masked everywhere in the UI (including the URL field, via a length-preserving mask), never logged, and excluded from Android cloud backup and device transfer (`dataExtractionRules` + `fullBackupContent`).
- No analytics, ads, or backend services. Network traffic goes only to configured RTMP/chat/web endpoints.
- Web sources use a WebView with JS enabled (required for widgets); only URLs the streamer configures are loaded.

---

## Why these choices

- **No screen capture.** MediaProjection would capture the operator sidebar, degrade quality, add latency, and require an extra permission. A GPU scene is the correct architecture for WYSIWYG.
- **No FFmpeg.** FFmpeg would add tens of MB of native code and a second muxing path. Hardware MediaCodec + a small RTMP client is smaller, lower-latency, and testable.
- **No CameraX dependency.** Camera2 gives explicit control over preview size, stabilization, zoom, and the SurfaceTexture needed by the compositor.
- **No vendor UVC SDK.** Generic UVC covers action cameras in webcam mode, which is the requirement; vendor SDKs would be brittle and violate the "no DJI hacks" constraint.

---

## Failure modes worth knowing

| Failure | Behavior |
|---|---|
| Destination TCP stalls | Watchdog closes it after ~15 s of no write progress; worker reconnects with backoff |
| Server drops after publish | Reader marks the publisher failed; worker reconnects; other outputs continue |
| UVC unplugged | Camera closes; built-in fallback if enabled; stream continues |
| Video encoder dies | Engine stops the stream and surfaces an error |
| Audio encoder dies | Stream continues silently with an error banner |
| Web virtual display denied | Snapshot fallback + warning; page frames update at ~8 fps |
| Compositor/EGL failure | Error surfaced; preview black; stream should be restarted |
| Foreground service rejected | Error surfaced; stream still runs while the app is foreground |
| Process killed while live | Service is `START_NOT_STICKY`; no zombie stream state |

---

## Tests

- `RtmpUrlTest` — URL parsing, ports, IPv6, masking.
- `Amf0Test` — AMF0 round-trips and known byte encodings.
- `H264Test` — Annex-B→AVCC, SPS/PPS extraction, AVCC record, FLV headers.
- `SettingsSerializationTest` — every source type/config, scene ordering, rect clamping, destinations.
- `RtmpPublisherIntegrationTest` — generates H.264/AAC with FFmpeg, publishes with `RtmpPublisher`, validates the server's recording with ffprobe (skipped without FFmpeg).

Device-dependent paths have a manual checklist in [TESTING.md](TESTING.md).
