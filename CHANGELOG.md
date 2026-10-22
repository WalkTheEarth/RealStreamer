# Changelog

All notable changes to RealStreamer. The project follows a simple `MAJOR.MINOR.PATCH` version in `app/build.gradle.kts`.

## 1.0.1 — 2026-10-22

### Added

- **Per-camera built-in selection.** Enumerates every Camera2 camera ID, including multiple rear lenses and duplicate logical cameras, with facing, focal length, and maximum resolution. The choice is saved; the default is the lowest-numbered camera for the selected lens.
- **Broadcast-deck visual system.** New near-black/acid-signal/magenta-on-air palette, cut-corner panels, hairline rules, square tally lamps, custom faders and toggles, and bundled Barlow / Barlow Condensed / IBM Plex Mono (OFL).
- **Reworked studio layout.** Program rail with camera chip and resolution; a single slim output/GO LIVE strip; wide layouts move the five transport controls into the streamer console. Portrait previews are capped so the transport stays above the fold.
- **Redesigned** welcome, destinations, overlays, settings, camera picker, and empty states.

### Fixed

- **Go Live failed on hardware**: `VideoEncoder` called `MediaCodec.start()` before `createInputSurface()`, which is only valid in the Configured state. Every hardware encoder rejected it. The encoder now configures, creates the input surface, then starts.
- **Black camera preview**: the nested SurfaceView never resolved through the Compose AndroidView layer on a Galaxy S23 FE despite live camera frames and compositor swaps. The preview is now a TextureView; see ADR-0007. The compositor still uses a single render with two blits.
- **Preview in the lower-left corner**: the preview blit normalized its quad against the 1280x720 FBO instead of the actual preview surface. It now uses the preview EGL width/height.
- **Sideways/cropped preview**: rotation now uses the physical `DisplayManager` rotation consistently in the camera open path and the display listener, and the compositor keeps a full-frame landscape buffer. The display listener also preserves the correct mirror flag instead of forcing the front-mirror setting onto the rear camera.
- **Default camera mismatch**: the fallback camera lookup now sorts IDs numerically, matching the first entry shown in the picker.

### Changed

- Preview consumer is a TextureView; ADR-0004 is superseded by ADR-0007.
- Version 1.0.1 / versionCode 2.
- Documentation, notices, and the testing checklist updated with the new camera model, preview architecture, font licenses, and on-device evidence.

### Verified on-device

Samsung Galaxy S23 FE (SM-S711B), Android 16, 1080×2340:

- all four exposed cameras listed and switchable;
- front/rear previews full-size and upright;
- 28-second local RTMP publish recorded 12.9 MB, probing as H.264 1280×720 + AAC-LC 44.1 kHz mono;
- live UI showed ON AIR, ~30 fps, ~3.4 Mbps; END returned to preview.

Still needs per-device coverage: UVC hotplug/format negotiation, WebView virtual displays, screen-off streaming on aggressive OEMs, and long multi-destination sessions. See [docs/TESTING.md](docs/TESTING.md).

## 1.0.0 — 2026-10-22

First complete Android release.

### Added

- WYSIWYG GPU compositor (EGL/GLES2): camera + overlays rendered once, blitted to preview and encoder.
- Multi-destination RTMP/RTMPS publishing from a single H.264/AAC encode; independent connection, reconnect/backoff, bounded queues, keyframe gating, timestamp rebasing, and per-output telemetry.
- Built-in Camera2 capture (back/front, stabilization, zoom, torch APIs, display-rotation updates) and generic UVC webcam support (hotplug, USB permission, MJPEG/YUYV fallbacks, built-in fallback).
- Broadcast overlays: text (dynamic tokens), live chat, stats, web sources (virtual display + transparent snapshot fallback), images (contain/cover).
- Streamer-only console: chat, stats/FPS/dropped frames, output health, audio meter; configurable visibility/width/panels.
- Chat: Twitch IRC (anonymous/OAuth), generic JSON WebSocket, demo messages; per-layer command filtering.
- Overlay editor with direct drag/resize, z-order, visibility, property sheets, photo-picker image import.
- Settings: video formats/fps/bitrate/keyframes, audio source/channels/bitrate/AEC/NS/AGC/mute, camera selection, chat, console.
- Foreground service + wake lock for screen-off streaming; notification with End stream.
- Private, debounced JSON persistence; masked stream keys/tokens; backup exclusion.
- Vendored, ABI-trimmed Apache-2.0 UVC engine; arm64-v8a and armeabi-v7a.
- RealStreamer icon set integrated (adaptive/legacy/monochrome, iOS/web assets reserved).
- Documentation: README, architecture, user/streaming/overlay guides, developer/security/FAQ/branding/roadmap, testing checklist, iOS port guide, ADRs, third-party notices.
- Tests: RTMP URL/AMF0/FLV/H.264, settings serialization, and a real FFmpeg RTMP publish/record integration test.

### Known limitations

- No iOS build (port guide included).
- No local recording, SRT, adaptive bitrate, multi-profile, or multiple simultaneous cameras.
- Audio always uses the phone microphone (no UVC/UAC audio).
- Web capture depends on ROM virtual-display support and WebView rendering; transparent sources are snapshot-based.
- Front-camera rotation and UVC format behavior require on-device verification (checklist provided).
- The Android package remains `com.irlstream.app`; the product name is RealStreamer.
