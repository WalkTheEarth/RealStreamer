# RealStreamer documentation

RealStreamer is an Android-first IRL broadcast studio: a WYSIWYG GPU compositor, generic UVC camera support, and one hardware encode fanned out to as many RTMP/RTMPS destinations as you configure.

> The internal Android package is still `com.irlstream.app` (and `com.irlstream.core` for the portable JVM module). That is an implementation detail for continuity; the product and launcher are **RealStreamer**.

## Start here

| If you want to… | Read |
|---|---|
| Install and use the app | [USER-GUIDE.md](USER-GUIDE.md) |
| Plan a stream, pick bitrate, hook up a UVC camera | [STREAMING-GUIDE.md](STREAMING-GUIDE.md) |
| Design a scene / understand every overlay source | [OVERLAYS.md](OVERLAYS.md) |
| Build, test, or extend the code | [DEVELOPING.md](DEVELOPING.md) |
| Understand the engine internals | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Verify on hardware | [TESTING.md](TESTING.md) |
| Know how keys are stored and what goes on the network | [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) |
| Port to iOS/iPadOS | [IOS-PORT.md](IOS-PORT.md) |
| See the icon set and brand assets | [BRANDING.md](BRANDING.md) |
| Check short answers / known issues | [FAQ.md](FAQ.md) |
| See what might come next | [ROADMAP.md](ROADMAP.md) |
| Understand why the architecture is what it is | [adr/](adr/) |

## Product in one paragraph

The camera — built-in or a USB Video Class webcam, including an action camera in webcam mode — is rendered into a single GPU scene with optional text, live chat, stats, web widgets, and images. The same scene is shown as the preview and fed to a hardware H.264 encoder. Audio is captured from the phone microphone and AAC-encoded. Encoded packets are copied to an independent RTMP publisher per destination, each with its own reconnect, backoff, queue, keyframe gate, and stats. A separate Compose-rendered sidebar gives the streamer chat, FPS/bitrate/dropped-frame stats, output health, and an audio meter; nothing in that sidebar is ever part of the broadcast. A foreground service keeps capture and publishing alive with the screen off.

## Documentation conventions

- Paths are relative to the repository root.
- Commands assume a Unix shell. Windows users can use the Gradle wrapper (`gradlew.bat`).
- "Broadcast layer" = a source composited into the encoded video.
- "Streamer-only panel" = local UI in the operator sidebar, never encoded.
- Stream keys are always shown masked in examples.
- The app is Android-first; iOS notes are a port plan, not a shipped target.

## Canonical sources of truth

- **Scene/user settings:** `core/src/main/kotlin/com/irlstream/core/model/Models.kt`
- **RTMP protocol:** `core/src/main/kotlin/com/irlstream/core/rtmp/`
- **Engine lifecycle:** `app/src/main/java/com/irlstream/app/engine/StreamEngine.kt`
- **Compositor:** `app/src/main/java/com/irlstream/app/engine/VideoCompositor.kt`
- **Fan-out:** `app/src/main/java/com/irlstream/app/engine/StreamPipeline.kt`
- **Protocol proof:** `core/src/test/kotlin/com/irlstream/core/rtmp/RtmpPublisherIntegrationTest.kt`
- **On-device checklist:** [TESTING.md](TESTING.md)

## Documentation status

The README, architecture, testing, iOS port, security, and user/streaming/overlay guides are current as of the 1.0.1 code in this repository. Built-in camera preview/selection, encoder startup, and a local RTMP publish were validated on a Samsung Galaxy S23 FE (Android 16). Device-dependent behavior that was not exercised there (UVC, per-ROM WebView virtual displays, screen-off behavior, foreground-service rules on other OEMs) is documented from implementation and API contracts and must be validated on each target device using the checklist.
