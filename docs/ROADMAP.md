# Roadmap

This is a direction document, not a commitment. The 1.0.x app deliberately stops at a focused, WYSIWYG single-profile Android broadcaster.

## Near term

- **On-device validation pass** using [TESTING.md](TESTING.md): orientation, UVC formats/hotplug, screen-off behavior, web capture on multiple ROMs, thermal/CPU baselines.
- **Front-camera orientation confirmation** on a spread of devices; adjust the mirrored rotation matrix if needed.
- **UVC resolution picker** (show supported modes instead of auto-selection), plus an explicit MJPEG/YUYV preference.
- **Web capture robustness**: detect a stalled virtual display (no frames) and fall back automatically; expose a frame-rate setting for snapshot mode.
- **Notification refinement**: per-output status, a "reconnect all" action, and a stronger warning when all outputs are down.

## Multi-profile and scenes

- Multiple named scenes with transitions (cut/fade) and a scene hotbar.
- Multiple profiles with export/import (portable scene JSON).
- Duplicate a layer; lock a layer; align/distribute tools; safe-area guides.
- A color/background source and optional camera PiP transform (corner, size, radius).

## Media and protocol

- **Local recording** (MP4 from the same encoded packets) with audio/video muxing and a storage-permission-free MediaStore integration.
- **SRT output** alongside RTMP (requires a native SRT library; separate from the current pure-Kotlin RTMP core).
- **Adaptive bitrate / congestion response** based on per-destination backpressure and drop rates.
- **Hardware encoder capability picker** (profile/level/bitrate mode) and a compatibility profile for stubborn devices.
- **Audio input selection** including UAC (USB) audio if a maintained UVC/UAC stack is adopted.
- **Multiple simultaneous camera sources** (e.g., UVC + built-in PiP) if GPU/thermal budgets allow.

## Chat and platform integration

- YouTube Live Chat (Data API, OAuth) and Kick/TikTok via documented APIs or a first-party relay.
- Emote image rendering in chat overlays (texture atlas + per-message layout).
- Chat moderation actions from the console (delete/timeout) where the platform API allows.
- Stream health alerts (destination down, bitrate collapse) as local notifications.

## iOS / iPadOS

- Evaluate external UVC camera availability through AVFoundation on target iPadOS/iOS versions.
- Metal compositor + VideoToolbox/AudioToolbox + RTMP (port the core protocol or use HaishinKit).
- SwiftUI operator console; snapshot-only web sources unless a better offscreen capture mechanism exists.
- Address background capture limitations explicitly in the product design; iOS cannot match the Android foreground-service model.

## Engineering

- Instrumented/device tests for the compositor, encoders, UVC, and foreground service.
- Screenshot/golden tests for the Compose UI.
- A benchmark harness for compositor frame time and encoder throughput across devices.
- Encrypted key storage using the Android Keystore (with migration from the current JSON).
- Accessibility audit (TalkBack labels, large-font layouts, contrast).

## Explicitly out of scope for 1.0.x

- Screen/game/window capture (would break the operator-privacy design).
- Windows/macOS/Linux desktop apps.
- Cloud relay or hosted ingest.
- 4K encoding.
- A plugin system.

Priorities will be driven by real on-device use: stability of the capture/compositor/encoder path first, then operator ergonomics, then platform breadth.
