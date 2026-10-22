# RealStreamer user guide

This is the complete operator manual. For stream planning and network math, read [STREAMING-GUIDE.md](STREAMING-GUIDE.md). For layer-by-layer scene design, read [OVERLAYS.md](OVERLAYS.md).

---

## 1. What you need

- An Android phone running Android 8.0 (API 26) or newer with a hardware H.264 encoder. Most phones from 2018 onward qualify.
- A data connection with enough **upload** bandwidth for every destination combined (see the streaming guide).
- For an external camera: USB‑C or micro‑USB OTG support, a data-capable cable/adapter, and a camera that offers **UVC webcam mode** (the DJI Action 4 does; so do most recent action cameras and many mirrorless cameras).
- The RealStreamer APK (debug or signed release) installed on the phone.
- RTMP ingest URLs + stream keys for each destination.

RealStreamer captures audio from the **phone microphone**, not from the USB camera. Use the phone's mic, a headset, or a separate mic connected to the phone.

---

## 2. Install

```bash
# from the repository root
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch **RealStreamer**. The debug build installs as a separate app (`com.irlstream.app.debug`); the release build uses `com.irlstream.app`.

On first launch you see the welcome screen. Nothing is captured or transmitted until you enter the studio and grant permissions.

---

## 3. First-run flow

1. **Welcome → Set up outputs** opens Destinations. **Enter studio** skips straight to the studio.
2. In the studio, the permission card asks for **camera and microphone**. Tap **Allow camera & mic**. Android may also ask for **notifications** — allow this so the live notification and its End stream action are visible (the service still runs if denied, but you lose the notification).
3. If no dialog appears, the permission was permanently denied: open Android Settings → Apps → RealStreamer → Permissions and enable Camera, Microphone, and Notifications.
4. The preview appears once the camera opens. If it stays black, see [FAQ.md](FAQ.md) or the troubleshooting table at the end of this guide.

---

## 4. Screen tour

### Studio (landscape / wide)

```
┌───────────────────────────────────────────────┬───────────────────────┐
│  [icon] REALSTREAMER   LIVE/READY   [camera]   │  STREAMER CONSOLE      │
│                                               │  LOCAL ONLY            │
│                                               │  ┌─────────────────┐   │
│              WYSIWYG camera +                 │  │ Chat            │   │
│              broadcast overlays               │  └─────────────────┘   │
│                                               │  ┌─────────────────┐   │
│                                               │  │ FPS / bitrate   │   │
│  destination pills                            │  │ dropped / uptime│   │
│  [FLIP] [MIC] [LAYERS] [OUTPUTS] [CONFIG]     │  │ outputs         │   │
│  [        GO LIVE / END STREAM        ]       │  └─────────────────┘   │
└───────────────────────────────────────────────┴───────────────────────┘
```

- **Top bar**: brand, live/preview badge and timer, camera-source chip, and shortcuts to Overlays, Destinations, and Settings.
- **Preview**: exactly what the encoder receives — the camera plus every enabled broadcast layer. Letterboxed to the stream aspect with black bars when the pane is a different shape.
- **Destination pills**: one per configured destination with a status dot and live bitrate. Tap to manage outputs.
- **Controls**: flip camera, mute mic, open the overlay editor, open destinations, or open settings. The big button starts/ends the stream.
- **Streamer console** (right): local-only chat, stats, output status, and audio meter. The **LOCAL ONLY** chip and the note under the header are deliberate: this column is not encoded. The hide (eye) icon collapses it; the layers icon opens the panel configuration.

### Studio (portrait / narrow)

The preview is at the top with the camera chip and destination pills, controls below it, and the streamer console stacked underneath. The whole page scrolls when needed. Portrait streaming is fully supported; the layout simply stacks instead of using a side column.

### Overlays

Two tabs:

- **On stream** — the live canvas with draggable selection boxes and the layer list. Add text, chat, stats, web, and image layers. Changes composite immediately, even while live.
- **Streamer only** — add/remove/reorder the private sidebar panels: Chat, Stats, Outputs, Audio. These never enter the encoder.

Both are reachable from Settings → Sources & outputs, the studio's LAYERS control, and the top bar.

### Destinations

The multi-output manager. Each card has a platform mark, name, host, masked URL, status chip, and enabled switch. Tap a card to edit the name, full URL, platform preset, enabled state, and auto-reconnect. You can add destinations before or during a stream.

### Settings

Sources & outputs shortcuts, video, audio, camera, chat, streamer-console width/visibility, and About. See section 10 for every setting.

---

## 5. Configure destinations

For each destination you need the **full ingest URL including the stream key**. RealStreamer parses the first path segment as the RTMP app and the remainder as the stream name.

1. Destinations → **+** (or Add destination).
2. Choose a preset (Twitch, YouTube, Kick, Custom). Presets fill a URL prefix and a default name; they do not fill your key.
3. Paste the complete URL.
4. The stream key is masked in the field after the app path. Use the eye toggle to reveal it while editing. The saved card always shows the masked form.
5. Enable **Enabled** and (usually) **Auto-reconnect**.
6. Save.

Common URL shapes:

| Service | URL shape |
|---|---|
| Twitch | `rtmp://live.twitch.tv/app/<stream key>` or `rtmps://live.twitch.tv/app/<stream key>` |
| YouTube | `rtmp://a.rtmp.youtube.com/live2/<stream key>` |
| Kick | The RTMPS ingest URL and key from Kick's stream settings |
| Custom | `rtmp://host[:port]/app/<stream name>` or `rtmps://host[:port]/app/<stream name>` |

The stream name may contain slashes and query parameters (e.g. `...?bandwidthtest=true`); everything after the first app segment is treated as the stream name.

**Multiple destinations:** add as many as you need. RealStreamer encodes once and copies the encoded packets to every enabled destination. Each has its own connection, queue, reconnect, and bitrate reading. A destination that drops does not stop the others.

**Editing while live:** changes apply immediately. Changing the URL or platform restarts that output's connection; the others keep running. Changing just the name/enabled/auto-reconnect updates in place.

---

## 6. Choose a camera

Tap the **camera chip** (top bar or above the preview) or go to Settings → Camera.

- **Built-in back camera** — default; best for IRL.
- **Built-in front camera** — selfie framing, mirrored if the option is on.
- **UVC / USB webcam** — any standard USB Video Class camera. Put an action camera in **Webcam mode** first.

When you select a UVC camera and it has not been used before, Android shows a USB permission dialog. Grant it; the device is remembered by vendor/product ID. If you deny it, RealStreamer reports the error and falls back to the built-in camera when the fallback switch is on. Use **Refresh** to rescan after connecting or replugging.

- **Fall back to built-in camera** (on by default): if the UVC camera disappears or fails, the built-in camera keeps the stream alive.
- **Mirror front camera**: flips the front preview horizontally. It does not affect the UVC camera or the back camera.
- **UVC is generic** — RealStreamer uses no vendor PID/VID tables, no action-camera companion apps, and no proprietary protocols. If the camera exposes a standard UVC video interface, it is a candidate.

Portrait streams rotate a landscape UVC frame 90° automatically. If a particular camera's orientation is wrong, use a landscape video format (Settings → Video) and report the device.

---

## 7. Build a scene

Go to **Overlays → On stream**.

1. Tap **Add on-stream source** (or + in the top bar).
2. Choose Text, Chat overlay, Stats overlay, Web source, or Image/logo.
3. The layer appears in the list and on the canvas. Drag inside its box on the preview to move it; drag the bottom-right handle to resize.
4. Tap the pencil on the layer row to edit properties (text, font, colors, chat behavior, web URL, image, stats fields).
5. Use the eye icon to hide a layer without deleting it; the up/down arrows change compositing order (the top row composites last).
6. The preview is the broadcast: what you see here, viewers receive.

Detailed per-source guidance and OBS mappings are in [OVERLAYS.md](OVERLAYS.md).

### Streamer-only panels

Go to **Overlays → Streamer only**. Add Chat, Stats, Outputs, or Audio. They appear immediately in the right sidebar (or below the preview in portrait) and are **never encoded**. Reorder them, hide individual panels, or remove them. The console as a whole can also be hidden from the console header or Settings → Streamer console.

This is the chart's green column: it is for the camera operator, not the audience.

---

## 8. Chat

Settings → Chat.

- **Twitch**: enter the channel name. Anonymous read-only IRC works for public channels. For restricted channels, paste an OAuth token (without the `oauth:` prefix; a pasted prefix is stripped) and the Twitch account name. The token is masked in Settings and stored in app-private storage.
- **Custom WebSocket**: a `ws://`/`wss://` endpoint that sends JSON. Configure the field paths for the user, message, and color (dot paths such as `data.user`). Plain text messages are accepted too; they appear as author "chat".
- **Demo messages**: local sample chat every ~3.4 s, useful for positioning an overlay without a real channel.
- **Hide command messages in overlays**: messages starting with `!` are removed from on-stream chat layers. The streamer console still shows them. Each chat layer also has its own "hide command messages" switch.

Chat appears in two places: the private chat panel and any on-stream chat layer you add. The panel's header shows connection status.

---

## 9. Web sources

Settings/Overlays → add a **Web source**, then enter an `https://` (or `http://`, `file://`, `data:`) URL. The page is rendered offscreen and composited into the scene.

- **Transparent background** (default): snapshot capture at roughly 8 fps, preserving alpha. Best for chat boxes, alert widgets, and lower thirds.
- **Opaque background**: virtual-display capture at the compositor's frame rate. Best for full-frame pages, dashboards, and animation. Some ROMs restrict app-created virtual displays; RealStreamer falls back to snapshot mode and shows a warning in the layer editor.
- **Zoom**: text zoom percentage.
- **Reload**: auto-reload every 30/60/300 s, or never.

Some sites block embedding, require DRM, open pop-ups, or are too heavy for a WebView. A blank/static web layer is usually the page, not the compositor. Test the URL in a normal browser first.

---

## 10. Settings reference

### Video

| Setting | Default | Notes |
|---|---|---|
| Format | 1280×720 | Presets: 720p30, 1080p30, 1080p60, 9:16 (720×1280). Also available in portrait via the 9:16 preset. |
| Frame rate | 30 | 24/30/60. The camera may deliver a different rate; the compositor renders at the target. |
| Video bitrate | 3500 kbps | 1–12 Mbps in 250 kbps steps. Per-destination bandwidth is this value (plus audio). |
| Keyframe interval | 2 s | 1/2/4 s. Shorter helps destinations start faster; longer saves bitrate. |

Changes while live are saved and marked pending; they apply on the next stream. The active preview/stream keeps the old format until then.

### Audio

| Setting | Default | Notes |
|---|---|---|
| Input | Voice / AEC | VOICE_COMMUNICATION (AEC-friendly), CAMCORDER, MIC, raw VOICE_RECOGNITION. |
| Bitrate | 128 kbps | 64–320 kbps in 32 kbps steps. |
| Channels | Mono | Mono or stereo. |
| Echo cancellation | On | Uses the platform AEC when available. |
| Noise suppression | On | Platform NS when available. |
| Automatic gain | Off | Platform AGC when available. |
| Mute | Off | Video continues; audio packets pause. |

Audio changes while live are pending until the next stream, except mute, which takes effect immediately. Audio always comes from the phone microphone.

### Camera

Built-in back/front, UVC device list with refresh, fallback-to-built-in, and mirror-front. See section 6.

### Chat

Twitch channel, optional token/account, custom WebSocket + field paths, demo messages, hide-command messages. See section 8.

### Streamer console

Show/hide the sidebar and choose its width (280–420 dp; the wide layout caps it relative to the screen). The console is local-only.

### Sources & outputs

Shortcuts to Overlays and Destinations.

### About

Version, architecture summary, and third-party acknowledgements.

---

## 11. Going live

1. Add at least one enabled destination with a valid URL.
2. Grant camera and microphone. The preview must be showing the camera.
3. Tap **GO LIVE**.
   - The button changes to **END STREAM** and the top badge becomes **LIVE** with a timer.
   - Each destination transitions CONNECTING → LIVE (or RETRYING/ERROR with a reason).
   - A foreground notification appears with duration, output count, bitrate, and an **End stream** action.
4. The stream continues if you switch apps or turn the screen off. Return to the app to restore the preview.
5. End with **END STREAM** or the notification action. Outputs close, the notification disappears, and the studio returns to preview.

### While live

- **Switch cameras** from the camera chip; the stream continues with a brief freeze while the new source opens.
- **Mute/unmute** instantly from the mic control.
- **Edit overlays** from any screen; changes are composited immediately.
- **Add/edit destinations**; each new output connects independently. Disabling one disconnects it without affecting others.
- **Change chat**; the new source reconnects.
- **Video/audio quality** changes are pending until the next stream.
- **Hide the console** with the eye icon to give the preview the full width.
- **Stop from the notification** if the app is in the background; capture shuts down cleanly.

### If something fails

- If a destination cannot connect, the others continue. The failed output retries with exponential backoff (0.5 s → 15 s) when auto-reconnect is on.
- If the video encoder fails, the stream stops and an error is shown.
- If audio fails, the stream continues silently with an error.
- If the camera is lost, the last frame remains (UVC falls back to built-in when enabled); switch cameras or reconnect.
- If capture is stopped from the background, RealStreamer closes the camera/mic and releases the compositor rather than leaving capture running without a visible preview.

---

## 12. Geometry, orientation, and the preview

- The preview pane uses the **active** stream aspect. While live, pending video changes do not resize the preview; the next stream uses the new format.
- The activity locks to the stream orientation (landscape or portrait) while in the studio/overlay editor so the camera framing is predictable. Other screens rotate freely.
- Camera frames are cover-fitted (cropped, never stretched) to the stream canvas.
- Text/chat/stats scale from a 1080p reference, so a 720p stream keeps the same proportions.
- Overlay positions are normalized 0..1, so a scene survives resolution changes.

---

## 13. Data, profiles, and privacy

- One profile. Scene, destinations, and settings are saved together in `filesDir/realstreamer-settings.json` (debounced writes).
- Stream keys and tokens are masked in the UI and never logged. They are stored unencrypted in private app storage and excluded from Android cloud backup/device transfer.
- There is no analytics or backend. See [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md).
- Uninstalling the app removes the profile and imported overlay images.

---

## 14. Quick troubleshooting

| Symptom | Check |
|---|---|
| Black preview | Camera permission; another app using the camera; USB permission for UVC; logcat `VideoCompositor`, `Camera2Controller`, `UvcController`. |
| No UVC camera in the list | Camera webcam mode; data-capable cable/adapter; OTG support; USB permission; tap Refresh. |
| UVC opens then falls back | Camera rejected MJPEG/YUYV at the requested size; try a different cable/hub or camera mode. |
| Destination stuck RETRYING | URL/key; outbound network; server status; tap the card or console row for the error. |
| No audio | Microphone permission; input source; mute state; another app holding the mic. |
| No chat | Twitch channel spelling; token for restricted channels; custom WS URL/fields; status line. |
| Stream dies with screen off | Start from the foreground studio; allow notifications; check OEM battery optimization. |
| Web layer blank | Page blocks embedding/DRM; try an opaque page; check the layer error; test the URL in a browser. |
| Overlay handles invisible | Device/ROM SurfaceView interop issue; use the layer list controls instead and report it. |
| App won't launch after install | Android version < 8.0; hardware encoder missing; reinstall the matching package. |

Logcat tags: `StreamEngine`, `VideoCompositor`, `VideoEncoder`, `AudioEngine`, `Camera2Controller`, `UvcController`, `WebOverlayManager`, `StreamingService`.

---

## 15. Glossary

- **Broadcast layer** — a scene source composited into the encoded video (text, chat, stats, web, image).
- **Streamer-only panel** — local sidebar UI; never encoded.
- **Destination** — one RTMP/RTMPS endpoint with its own stream key and connection.
- **Fan-out** — copying one encoded stream to multiple independent publishers.
- **WYSIWYG** — the preview and the outgoing video are the same compositor output.
- **UVC** — USB Video Class, the standard driverless webcam protocol.
- **Keyframe gate** — a destination waits for a keyframe before sending media so a late connection is decodable.
- **Timestamp rebasing** — starting a new RTMP connection at ~0 ms instead of the stream's absolute elapsed time.
