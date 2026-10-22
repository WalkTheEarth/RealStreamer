# Streaming guide

Everything about planning and running a RealStreamer broadcast: network math, encoding choices, UVC action-camera rigs, and ingest troubleshooting. For the UI walkthrough, see [USER-GUIDE.md](USER-GUIDE.md).

---

## 1. The one rule: upload bandwidth is shared

RealStreamer encodes **once** and copies the encoded packets to every enabled destination. That saves CPU, but it does **not** save upload bandwidth. Each RTMP connection carries its own copy of the stream.

```
per-destination upload ≈ video bitrate + audio bitrate + ~5–10% protocol overhead
total upload            ≈ (video + audio) × number of destinations × 1.08
```

Examples (video + 128 kbps audio):

| Stream | Per destination | 2 destinations | 3 destinations |
|---|---|---|---|
| 720p30 @ 3 Mbps | ~3.38 Mbps | ~6.8 Mbps | ~10.2 Mbps |
| 1080p30 @ 5 Mbps | ~5.54 Mbps | ~11.1 Mbps | ~16.6 Mbps |
| 1080p60 @ 8 Mbps | ~8.78 Mbps | ~17.6 Mbps | ~26.3 Mbps |

Plan to use **no more than 60–70%** of the measured uplink, leaving headroom for TCP retransmits, other apps, and mobile-network variability. A 20 Mbps home uplink can comfortably carry one 1080p30 stream or two 720p30 streams, not three 1080p30 streams.

Use a speed test on the actual network at the actual location, and re-test during busy hours. Wi-Fi is preferable when you control the access point; congested public Wi-Fi is often worse than a good LTE/5G connection. RealStreamer does not bond connections.

---

## 2. Encoding choices

Settings → Video.

| Scenario | Resolution / fps | Bitrate | Keyframe |
|---|---|---|---|
| Walking IRL, 1–2 outputs, mobile network | 720p30 | 2.5–4 Mbps | 2 s |
| Desk / event / stable Wi-Fi, 1–2 outputs | 1080p30 | 4.5–6 Mbps | 2 s |
| High-motion action, strong uplink | 1080p60 | 7–10 Mbps | 1–2 s |
| Vertical phone / short-form simulcast | 720×1280 @ 30 | 3–5 Mbps | 2 s |
| Very constrained uplink | 720p30 | 1.5–2.5 Mbps | 2 s |

Notes:

- **Shorter keyframes** (1 s) help destinations start promptly after a reconnect and improve recovery, at a modest bitrate cost. 2 s is a good default.
- **60 fps** doubles encoder work and can make motion look smoother, but it also raises the bitrate needed for the same quality. It does not make a weak uplink better.
- **Resolution above 1080p is not offered** in this version. Most IRL uplinks cannot sustain 4K and thermal budgets on phones are tight.
- The compositor renders at the selected frame rate. If the camera delivers 30 fps and you select 60, frames are duplicated; the preview/stream remain smooth but you are paying for 60 fps.
- The encoder uses a hardware H.264 surface encoder with CBR when the device supports it, otherwise VBR. Some devices ignore bitrate mode preferences; monitor the actual bitrate and quality.

### Audio

Phone mic, AAC-LC, 48 kHz, mono by default, 128 kbps. Stereo at 160–192 kbps is fine on a strong uplink; mono is more robust and smaller. Enable echo cancellation for speakerphone/outdoor voice; disable it if it pumps or clips a dedicated mic. Noise suppression helps wind/road noise but can dull music; test.

The USB camera's built-in audio is **not** captured. If you need the action camera's mic, connect a separate mic to the phone or use the phone's mic near you.

---

## 3. Destination URLs and keys

A destination URL contains both the server and your secret stream key. Treat the full URL like a password.

- Twitch: `rtmp://live.twitch.tv/app/<key>` or `rtmps://live.twitch.tv/app/<key>`
- YouTube: `rtmp://a.rtmp.youtube.com/live2/<key>`
- Kick: paste the RTMPS ingest URL and key from Kick
- Custom: `rtmp[s]://host[:port]/app/<stream name>`

RealStreamer masks the key everywhere after the app path, both in the field and on the destination card. The key is stored in app-private storage and excluded from backups. Do not screen-share the destination editor with the key revealed.

Rotate keys if they may have leaked. Each platform's dashboard has a reset action.

### RTMPS / TLS

`rtmps://` URLs use TLS with hostname verification. Use RTMPS where offered, especially on untrusted networks. Plain RTMP sends media and the stream key in the clear over the internet. (The local FFmpeg test uses plain RTMP on localhost only.)

### Custom servers

The first path segment is the RTMP app; the rest is the stream name, including any `?query`. If your provider uses a multi-segment app path, the current parser will treat only the first segment as the app. Use a URL form that matches this convention or adjust `RtmpUrl.parse` in the core module.

---

## 4. UVC action-camera rigs (DJI Action 4 and friends)

RealStreamer uses generic **USB Video Class**, not a vendor SDK. The camera must expose UVC webcam mode.

Typical setup:

1. Put the camera in **Webcam / UVC** mode (DJI Action 4: Webcam mode; other cameras: "USB webcam", "PC webcam", etc.). The camera should appear as a video device, not a storage volume.
2. Use a **data-capable** USB‑C cable and, if needed, an OTG adapter compatible with your phone. Charge-only cables are a common failure.
3. Connect the camera to the phone. Android shows a USB permission dialog for the camera; allow it. The device appears in the in-app camera picker and Settings → Camera.
4. Select it. RealStreamer chooses a supported MJPEG/YUYV size near your output resolution and starts the preview.
5. Mount the camera so the frame is stable. Enable the camera's own stabilization if it has it; RealStreamer also requests electronic stabilization for the built-in camera where supported (not for UVC).
6. Power: action cameras often charge while in webcam mode, but USB data + charging depends on the camera and hub. For long streams, use a powered hub or a camera with a large battery and verify the charge state.
7. Heat: 1080p UVC + encoding is warm work. Avoid direct sun, remove thick cases, and monitor for thermal throttling (dropped frames, encoder stalls).

### Audio for UVC rigs

The phone microphone is the audio source. Options:

- Phone in a pocket/chest rig with the mic exposed.
- Wired headset mic.
- A separate wireless mic receiver connected to the phone (if the phone supports it).

The camera's built-in mic is not available through this UVC path.

### Supported formats and sizes

The UVC stack tries the chosen size with the format reported by the camera, then MJPEG, then YUYV, and falls back to 1280×720 and 640×480. 1080p UVC usually requires MJPEG; YUYV at 1080p often exceeds USB bandwidth. If a camera opens at 640×480 and looks soft, that is the negotiated fallback, not the compositor.

### Multiple cameras

Only one camera source is active at a time. Built-in and UVC sources are alternatives; switch from the camera chip. Devices are keyed by vendor/product ID, so two identical cameras without serial-number selection are ambiguous — the first enumerated one wins.

---

## 5. Scene and overlay planning

The compositor is a full-canvas scene at the stream resolution. Design for the **smallest** screen your viewers use:

- Body text at 26–40 px-equivalent at 1080p (the defaults) stays legible at 720p and on phones.
- Keep a **safe margin** around the frame. Do not place text under platform UI (chat overlays, follow buttons) at the edges.
- Use a backdrop with alpha for text/chat; a shadow alone can disappear over bright scenes.
- Limit on-stream chat to 5–8 messages. More becomes unreadable and burns bitrate on text changes.
- Stats overlays are best kept small; they are for credibility/transparency, not decoration.
- Web widgets: test their readability at the layer size; zoom is text zoom, not a full CSS transform.
- A full-frame web source is fine, but it hides the camera; use it deliberately.

See [OVERLAYS.md](OVERLAYS.md) for every source and configuration.

---

## 6. Pre-flight checklist

Before every important stream:

- [ ] Uplink measured at the location; total destination bandwidth ≤ 60–70% of it.
- [ ] Each destination shows READY and a valid masked URL.
- [ ] Camera framing, focus, and stabilization checked in the preview.
- [ ] UVC camera powered, permission granted, fallback configured.
- [ ] Phone charged; battery optimization disabled for RealStreamer if the OEM is aggressive.
- [ ] Notification permission granted; a test notification appears.
- [ ] Mic level moves in the console; mute state correct.
- [ ] Overlay text/chat/stats readable; the streamer-only column is *not* in the preview.
- [ ] One dry run with a local RTMP listener or an unlisted key (see below).
- [ ] Thermal plan (shade, no thick case, break every ~45–60 min if needed).

### Dry run with a local RTMP server

On a computer on the same network:

```bash
./scripts/local-rtmp.sh 19350 /tmp/dryrun-a.flv
./scripts/local-rtmp.sh 19351 /tmp/dryrun-b.flv
```

Add `rtmp://<computer-LAN-IP>:19350/live/test` and `...:19351/live/test` as destinations, go live for 30 seconds, stop, and check both recordings with `ffplay`. This verifies capture, compositing, encoding, fan-out, and timestamps without touching a public platform.

---

## 7. Running the stream

- Watch the destination pills and the Outputs panel. A destination's status is independent; one retry is not a reason to end the whole stream.
- Watch FPS, bitrate, and dropped frames in the Stats panel. If FPS falls but the bitrate is high, the device may be thermals/CPU bound; lower resolution or fps.
- If a destination is RECONNECTING, check its message (tap the console row or destination card). Server errors, 401s, and network failures are different fixes.
- If the camera drops, the stream shows the last frame (or the built-in fallback). Switch cameras or replug.
- Editing overlays live is safe; the compositor applies changes on the next frame.
- Ending with the notification while backgrounded stops capture cleanly and releases the camera/mic. The engine will not leave the camera running without a foreground service.

---

## 8. Common ingest errors

| Message | Likely cause | Fix |
|---|---|---|
| `Server refused publish: NetStream.Publish.BadName` | Wrong key, expired key, or a URL app/stream split mismatch | Re-copy the full URL; check the app path |
| `Server refused publish: NetStream.Publish.Denied` | Account/stream disabled or region restriction | Check the platform dashboard |
| `Could not publish … timed out` | Network blocked, wrong host/port, DNS | Test a local listener; try another network; verify port 1935/443 |
| `Connection lost` repeatedly | Weak uplink/thermal shutdown | Lower bitrate/resolution, move to better network |
| TLS handshake/certificate error | RTMPS hostname/cert mismatch, captive portal | Try the documented RTMP URL or fix the network |
| Destination RECONNECTING forever with no server error | The server accepts TCP but not the publish | Check the key/app; they may be case-sensitive |

RealStreamer retries with backoff and requests a fresh keyframe on reconnect. Recordings from a reconnected output start near timestamp 0 (per-connection rebasing).

### Simulcasting policy

Platform rules about simultaneous streaming vary and change. Check each platform's terms before restreaming the same content to multiple services. RealStreamer provides the technical capability; it does not grant permission.

---

## 9. After the stream

- End from the app or the notification; verify the notification disappears.
- The camera remains in preview if the studio is visible; it is released when you leave/background without a live stream.
- Imported overlay images and settings persist. Reconnecting the same UVC camera uses the saved vendor/product key.
- If you stopped from the notification while backgrounded, the camera/mic and compositor are released automatically.

---

## 10. Failure and recovery matrix

| Failure | App behavior | Operator action |
|---|---|---|
| One destination drops | Others continue; failed output retries | Check the console message; fix the URL/network |
| All destinations drop | Each retries independently | Check the uplink; the encode/preview continue |
| Video encoder fails | Stream stops with an error | Restart the stream; lower resolution; check thermals |
| Audio encoder fails | Stream continues silently + error | Restart; check mic/permissions |
| UVC unplugged | Fallback to built-in if enabled | Replug and reselect; the stream continues |
| Camera error (built-in) | Last frame remains; error | Switch camera / restart preview |
| Web source fails | Snapshot fallback + warning | Fix the URL/page; remove the layer |
| App backgrounded while live | Foreground service + wake lock keep running | Return to restore preview |
| App killed by OEM | Stream stops | Disable battery optimization for RealStreamer |
| Foreground service rejected | Error; stream runs while foreground | Start from the studio; allow notifications |
