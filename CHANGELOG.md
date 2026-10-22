# Changelog

All notable changes to RealStreamer. The project follows a simple `MAJOR.MINOR.PATCH` version in `app/build.gradle.kts`.

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
