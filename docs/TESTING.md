# Testing

The automated suite is JVM-only. The camera/preview/encoder/live-RTMP path was additionally exercised on a **Samsung Galaxy S23 FE (SM-S711B), Android 16, 1080×2340** on 2026-10-22:

- all four exposed cameras listed (back 0, back 2, front 1, front 3);
- front and rear previews rendered full-size and upright, and per-camera selection persisted;
- a 28-second local RTMP publish to `ffmpeg -listen 1` recorded **12.9 MB** and probed as **H.264 1280×720 + AAC-LC 44.1 kHz mono**;
- the live UI showed ON AIR, 30–31 fps, ~3.4 Mbps, and END returned to preview.

UVC, web sources, screen-off, per-ROM VirtualDisplay behavior, and multi-destination recovery still need coverage on each target device. Use the checklist below as the handoff.

---

## Automated

```bash
./gradlew :core:test          # unit + RTMP integration test
./gradlew :app:lintDebug      # 0 errors expected
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

### RTMP integration test

`RtmpPublisherIntegrationTest`:

1. uses FFmpeg to generate a 2-second H.264 (baseline) + AAC FLV file;
2. parses the FLV to obtain the AVCC config, AAC AudioSpecificConfig, and frames;
3. starts `ffmpeg -listen 1 -i rtmp://127.0.0.1:PORT/live/test -c copy out.flv`;
4. publishes with `RtmpPublisher` (our hand-written RTMP implementation);
5. validates the server's output with `ffprobe` (video + audio streams, decoded frame count > 0).

The test skips itself if FFmpeg is not installed. This is the guard that the protocol works against a real server implementation, not just our own parser.

### What is not automated

- OpenGL/EGL rendering (needs a GPU + Android runtime)
- Camera2 and UVC capture (needs hardware)
- Foreground service and screen-off behavior
- WebView virtual display capture
- Real Twitch/YouTube/Kick endpoints (they require live keys)

---

## Local multi-destination test

Run two independent RTMP listeners on your computer (same LAN as the phone):

```bash
./scripts/local-rtmp.sh 19350 /tmp/irl-a.flv
./scripts/local-rtmp.sh 19351 /tmp/irl-b.flv
```

Find your computer's LAN IP (`ip addr` / `ifconfig`), then add these destinations in the app:

```
rtmp://192.168.1.50:19350/live/test
rtmp://192.168.1.50:19351/live/test
```

Start the stream, wait ~10 seconds, stop, then:

```bash
ffprobe -v error -show_streams /tmp/irl-a.flv
ffplay /tmp/irl-a.flv
```

Both files should contain H.264 video and AAC audio. Stop one listener mid-stream (`Ctrl+C`) and confirm:

- the other destination stays LIVE;
- the stopped one shows RECONNECTING, retries with backoff, and recovers when restarted;
- the recovered recording starts at a timestamp near zero (timestamp rebasing).

---

## On-device checklist

Mark each item after the first install. Use `adb logcat -s StreamEngine EngineSurface VideoCompositor VideoEncoder AudioEngine Camera2Controller UvcController WebOverlayManager StreamingService`.

### First run and permissions

- [ ] Welcome screen renders; both CTAs work.
- [ ] Camera permission card appears in the studio; granting it opens the preview.
- [ ] Denying microphone leaves the preview working and Go Live requests the mic.
- [ ] Notification permission grant/denial does not break going live.
- [ ] Settings persist across a force-stop and relaunch.
- [ ] Stream keys are masked in Destinations and the edit sheet; the reveal toggle works.

### Built-in camera / compositor

- [ ] Every camera ID the phone exposes appears in the camera sheet and Settings → Camera, with a distinct facing/focal-length label.
- [ ] Selecting each camera reopens the sensor; the preview fills the program frame (not a corner/quarter) and the stream label includes the selected ID.
- [ ] With no explicit choice, the default is the lowest-numbered camera for the chosen lens and the sheet shows that same camera selected.
- [ ] Back camera previews upright in landscape orientation.
- [ ] Front camera previews upright and mirrored as configured.
- [ ] Rotating the phone 180° in landscape keeps the image upright (display listener).
- [ ] Switching resolution (720p30 → 1080p30 → 9:16) reflows the preview and restarts the camera.
- [ ] The preview aspect matches the configured stream; no stretching and no unexpected hard crop (a deliberately wider lens may show a wider field of view).
- [ ] Camera adjustment: 0/90/180/270 rotation changes the preview immediately without reopening the sensor; the encoded frame matches.
- [ ] Mirror image flips the live preview and the encoded frame; Reset clears both controls.
- [ ] Framing: Fit letterboxes a portrait rotated frame without cropping; Fill covers the frame. Switching updates preview and encoder live.
- [ ] Rotation/mirror persist across a force-stop and relaunch, and apply to UVC as well.
- [ ] WYSIWYG check: record one destination with FFmpeg and compare the recorded frame with the in-app preview (position of overlays, colors, crop).

### UVC

- [ ] Connect the DJI Action 4 in Webcam mode over OTG; grant USB permission.
- [ ] It appears in the camera chip sheet and Settings → Camera with the product name.
- [ ] Selecting it shows a UVC preview at a supported resolution.
- [ ] Unplug it while previewing: with fallback on, the built-in camera takes over and a warning is shown.
- [ ] Replug it: it can be selected again; auto-recovery does not loop permission dialogs.
- [ ] Deny USB permission: the app reports it and continues on the built-in camera if fallback is enabled.
- [ ] Verify the selected UVC mode on the camera matches (e.g., 1080p / 30 fps).

### Overlays

- [ ] Add a text layer; edit text, size, color; drag and resize on the preview.
- [ ] Add an on-stream chat layer; verify it appears in the preview and in the recorded output.
- [ ] Add an on-stream stats layer; verify live values update.
- [ ] Add an image/logo; pick from the photo picker; it survives an app restart.
- [ ] Add a web source:
  - [ ] transparent page: appears at snapshot rate, alpha preserved;
  - [ ] opaque page: smooth virtual-display capture (on ROMs that permit it);
  - [ ] a failing URL shows an error in the layer editor and does not crash.
- [ ] Reorder layers; verify the top layer wins.
- [ ] Delete a layer; the preview updates immediately.
- [ ] While live, changes are visible immediately in the outgoing stream.
- [ ] The streamer-only chat/stats/outputs/audio panels appear in the sidebar and are **absent** from the recorded stream. Compare with a viewer/recording.

### Audio

- [ ] Mic permission requested; audio recorded in both destinations.
- [ ] Mute pauses audio without stopping video; unmute resumes.
- [ ] AEC/NS/AGC toggles take effect on the next stream after changing settings.
- [ ] Audio/video stay in sync for a 2-minute stream.
- [ ] The level meter moves and mute state is reflected in the console.

### Chat

- [ ] Twitch anonymous read works for a public channel.
- [ ] Optional token + username works for a restricted channel.
- [ ] Custom WebSocket receives JSON messages using the configured field paths.
- [ ] Demo messages appear in the console and on the overlay.
- [ ] `!command` messages are hidden from the on-stream chat overlay but still visible in the streamer console (with the option enabled).
- [ ] Reconnect after network loss.

### Live / destinations

- [ ] Two destinations go live from one encode; both receive video/audio.
- [ ] Kill one server; the other stays live; the failed one retries and recovers.
- [ ] Disable a destination mid-stream; it disconnects, the other continues.
- [ ] Add a destination mid-stream; it connects without disturbing the others.
- [ ] End stream: outputs close, the notification is removed, the preview remains.
- [ ] Turn the screen off while live: streaming continues; the notification remains; turning it back on restores the preview.
- [ ] Press End stream in the notification: the stream stops and the app state returns to preview.
- [ ] Go Live reaches LIVE without an encoder exception; logcat must not contain `setInputSurface() is valid only at Configured state`.
- [ ] The server-side recording probes as H.264 video + AAC audio at the expected resolution; the first keyframe arrives promptly.
- [ ] 15+ minute stream: no A/V drift, no memory growth, no encoder stalls; monitor battery/temperature.

### Web / visual quality

- [ ] Text remains legible at 720p and 1080p.
- [ ] Chat line wrapping and max-message trimming behave.
- [ ] Stats colors/readability at the chosen size.
- [ ] Web overlay positioned correctly after resolution changes.
- [ ] Image overlay fit (contain/cover) behaves.

### Lifecycle and edge cases

- [ ] Navigate Studio → Overlays → Studio while not live; the camera reopens and the preview returns.
- [ ] Navigate while live; the stream is uninterrupted and the preview reattaches.
- [ ] Background the app while not live; camera/chat stop. Resume; they restart.
- [ ] Change video settings while live; the pending flag appears and the new format applies after stopping.
- [ ] Change audio settings while live; pending flag and next-stream application.
- [ ] Rotate / change orientation settings; the activity re-locks and the camera transform stays correct.
- [ ] Kill USB/camera during a live stream; fallback behavior and error reporting.

---

## Performance targets

- 720p30 @ 3–4 Mbps: < 15% CPU on a mid-range 2022+ phone (excluding software UVC MJPEG decode).
- 1080p30 @ 5–6 Mbps: stable 30 fps encoder output, no dropped frames on a healthy network.
- One encode to N destinations should scale linearly in bandwidth but not in encode cost.
- The compositor blends 1 camera OES texture + up to ~5 overlay 2D textures + web OES textures per frame. If a device struggles, lower the output resolution before removing overlays.

Record:
- encoder FPS / dropped frames (console);
- per-destination bitrate and retries;
- device temperature/battery over 30 minutes;
- A/V sync at 0, 5, 15, 30 minutes.

---

## Reporting a device issue

Include:
- device model, Android version, ROM;
- camera type (built-in/UVC, product, resolution/format);
- destination service and URL shape (never the key);
- relevant logcat lines from the tags above;
- whether the issue reproduces with one destination and with no overlays.
