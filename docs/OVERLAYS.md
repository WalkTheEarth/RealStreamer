# Overlays and scenes

RealStreamer's scene is an ordered list of sources at normalized coordinates. Broadcast sources are composited into the encoded video; streamer-only sources are local sidebar panels. This document covers every source, the coordinate model, and how the scene maps to OBS concepts.

---

## 1. Two audiences, one model

Every source has an `audience`:

- **BROADCAST** — drawn by the GPU compositor into the outgoing video. Visible to viewers.
- **STREAMER_ONLY** — drawn by Compose in the studio sidebar. Never encoded.

Source types:

| Type | Audience | Purpose |
|---|---|---|
| `TEXT` | Broadcast | Static or dynamic text (tokens) |
| `CHAT` | Broadcast | Live chat rendered into the video |
| `STATS` | Broadcast | FPS/bitrate/dropped/uptime/output count on screen |
| `WEB` | Broadcast | Offscreen WebView page/widget |
| `IMAGE` | Broadcast | Logo, watermark, full-frame graphic |
| `STREAMER_CHAT` | Streamer | Private chat reader |
| `STREAMER_STATS` | Streamer | Private stream telemetry |
| `STREAMER_OUTPUTS` | Streamer | Private per-destination status |
| `STREAMER_AUDIO` | Streamer | Private VU meter + mute |

The separation is structural, not a visibility flag: the compositor iterates only `scene.broadcastSources`, and the console iterates only `scene.streamerSources`. There is no code path that encodes a streamer-only source.

---

## 2. Coordinates and compositing

- Positions/sizes are **normalized** `0..1` relative to the stream canvas: `x`, `y`, `width`, `height`.
- `order` is the compositing order; higher numbers draw later (on top). The on-stream layer list shows the topmost layer first.
- The camera is the background layer and always fills the canvas with **cover** fit (cropped, never stretched).
- Overlay bitmaps are rendered at the layer's pixel size at the current output resolution. Font sizes are specified at a 1080p reference and scale with `outputHeight / 1080`.
- The compositor renders the scene once into an offscreen texture and blits it to both the preview and the encoder. The preview is the broadcast.
- The overlay editor's boxes/handles are Compose UI above a SurfaceView; they are not part of the scene.

### Editing directly

- Tap a layer on the canvas or in the list to select it.
- Drag inside the box to move.
- Drag the bottom-right handle to resize (minimum 5% of the canvas).
- The list provides precise toggles and reordering; the properties sheet provides numeric/visual settings.
- Overlays → On stream, all changes composite immediately, even while live.

### Limits

- Layers are clamped to the canvas (`NormalizedRect.clamped()`); a layer cannot be moved off-canvas.
- The compositor prunes textures for removed/disabled layers; disabled layers keep their config.
- Very small rects (<4 px) are skipped by the renderer.

---

## 3. Text

Settings: text, font size (16–96 px at the 1080p reference), text color, backdrop color incl. transparent, alignment, bold, dynamic tokens, layer name, visibility.

- Text is centered vertically in its rect, wraps within the padding, and truncates after 3 lines.
- A rounded backdrop is drawn when alpha > 0.
- A subtle drop shadow keeps text readable on bright scenes.
- The backdrop fills the whole layer rect; size the rect around the text rather than making a full-width bar unless that is intentional.

### Tokens

When **Dynamic tokens** is on, the text is re-rendered every second with:

| Token | Value |
|---|---|
| `{time}` | Local `HH:mm:ss` |
| `{uptime}` | Stream duration (`mm:ss` or `h:mm:ss`) |
| `{fps}` | Encoder output FPS (1 decimal) |
| `{bitrate}` | Video bitrate (`x.x Mbps`) |
| `{resolution}` | Active stream resolution |
| `{destinations}` | Live outputs / enabled outputs |

Example:

```
LIVE · {time}
{resolution} · {bitrate}
```

Turn tokens off for a static sign.

---

## 4. Chat overlay

Settings: max messages (3–12), font size (16–48 px), accent color, panel color/alpha, show names, show timestamps, hide command messages.

- The panel has a rounded background, an accent header ("LIVE CHAT"), a message count, and bottom-aligned newest messages. Older lines are dropped to fit.
- Names use each platform's user color, or a deterministic palette fallback.
- Messages are plain text, not HTML; emote names appear as text unless the platform provides images (they do not here).
- Command filtering is per layer; the streamer console remains unfiltered. The global Settings → Chat toggle also strips `!commands` from all broadcast chat layers.
- The chat source config and the chat connection are separate: the layer controls presentation; Settings → Chat controls where messages come from.

Recommended placement: right side, ~29% width, 50–65% height (the default), or a lower-third band. Keep it out of platform UI safe areas.

---

## 5. Stats overlay

On-screen telemetry for transparency: FPS, bitrate, dropped frames, uptime, output count. Each row is optional; font size and accent/panel colors are configurable.

The values are the same aggregate telemetry that drives the streamer console: encoder FPS, encoded video bitrate, compositor+pipeline drops, stream uptime, and destination states. It is an on-stream snapshot, not the full per-destination console.

---

## 6. Web source

Settings: URL, transparent background, zoom %, reload interval (off/30/60/300 s).

Two capture modes:

| Mode | When | Characteristics |
|---|---|---|
| Virtual display | Transparent **off**, and the ROM permits it | `VirtualDisplay` + `Presentation` + WebView, produced into an OES texture. Full compositor frame rate. |
| Snapshot fallback | Transparent **on**, or virtual display unavailable | Detached WebView drawn to a Bitmap ~8 fps. Preserves alpha. Lower fidelity. |

Practical notes:

- HTML/CSS/JS, DOM storage, media autoplay (user-gesture requirement disabled), cookies, and mixed content compatibility are enabled.
- Some pages refuse embedding, require DRM/widevine, spawn pop-ups, or use a user agent that dislikes WebViews. Test in a browser first.
- Video/canvas content may not appear in snapshot mode.
- The WebView is offscreen; there is no interaction with the page from the studio.
- The URL must use `http://`, `https://`, `file://`, `data:`, or `about:`. Other schemes are ignored.
- Typing a URL is debounced in the engine (450 ms) so captures are not restarted per keystroke.
- The layer editor shows capture warnings from the engine; a warning does not necessarily mean the layer is blank.

Good uses: StreamElements/Streamlabs-style widgets, a local dashboard served from `file://`, a clock, an alert box, a scrolling ticker.

Risks: a page you do not control can track requests from the phone; JS is enabled by design. Only load sources you trust.

---

## 7. Image / logo

- Import from the Android photo picker. The file is copied into app storage (`filesDir/overlays/`) so it survives restarts.
- **Contain** fits the whole image inside the layer rect; **Cover** fills and crops to the layer bounds (GPU scissor clipping).
- Images are decoded to a max 2048 px dimension to bound memory.
- PNG alpha is preserved and premultiplied through the compositor.
- Use Covers for full-frame backgrounds, Contain for logos/watermarks.

---

## 8. Streamer-only panels

| Panel | Contents |
|---|---|
| Chat | Latest messages with platform dots, colors, and connection status |
| Stats | FPS, bitrate, dropped, uptime, a bitrate sparkline, output count |
| Outputs | Per-destination status, bitrate, retry count, and error text |
| Audio | VU meter, input source, mute control |

They are reorderable and individually hideable. The whole console has a visibility toggle and width. The console header says **LOCAL ONLY** and the subheader "Chat and stats here are never broadcast." This is the design chart's side column.

The console is deliberately not a scene source: it cannot be accidentally captured because no screenshot/MediaProjection path exists.

---

## 9. Example scenes

### Minimal IRL (default camera + private console)

```
Broadcast: camera only
Streamer:  Chat, Stats, Outputs, Audio
```

### Chat on the right (the design chart)

```
Camera: full canvas
Broadcast:
  Chat        x 0.67  y 0.04  w 0.29  h 0.62
  Text        x 0.04  y 0.78  w 0.55  h 0.14   "LIVE · {time}"
  Stats       x 0.04  y 0.04  w 0.34  h 0.16
Streamer:
  Stats (dropped/FPS/bitrate), Chat
```

### Lower-third name / location

```
Text   x 0.04 y 0.74 w 0.50 h 0.16   "ON LOCATION · {time}"
Chat   x 0.68 y 0.30 w 0.29 h 0.50   max 5 messages
Image  x 0.04 y 0.04 w 0.14 h 0.14   logo
```

### Full-frame web dashboard

```
Web    x 0 y 0 w 1 h 1   opaque, 0% zoom, reload 60s
Image  x 0.78 y 0.04 w 0.18 h 0.18   camera PiP logo
```

### No-overlay stream

```
Broadcast: none
Streamer:  Chat, Stats
```

The camera is the whole frame; the console is still available locally.

---

## 10. Mapping from OBS

| OBS | RealStreamer |
|---|---|
| Scene | Scene (one profile) |
| Video Capture Device (webcam) | Camera source (built-in or UVC) — always the background |
| Text (GDI+) | Text layer |
| Browser Source | Web source |
| Image Source | Image layer |
| Chat dock (streamer-only) | Streamer-only Chat panel |
| Stats dock (streamer-only) | Streamer-only Stats/Outputs/Audio panels |
| Multiple RTMP outputs | Destinations (one encode, many publishers) |
| Studio Mode preview/program | Not present; the single preview is the program |
| Color Source | Not a separate source; the scene background color is currently fixed black |
| Media Source (local video) | Not supported |
| Window/Game Capture | Not supported (Android; no screen capture by design) |
| Filters (LUT, chroma key, blur) | Not supported (camera image passes through; UVC camera controls are camera-side) |
| Transition between scenes | Not supported (one scene) |
| Hotkeys | Not supported |

The important semantic difference: OBS's "streamer-only overlay" is a dock; RealStreamer models it as a source with `STREAMER_ONLY` audience so it is structurally excluded from the compositor.

---

## 11. Performance and legibility

- Each enabled text/chat/stats layer owns a 2D texture. They are re-rendered only when their content key changes (config, dimensions, token values, messages, stats).
- Chat re-renders on new messages; stats once per second; text with `{time}` once per second; static text only when edited.
- Web textures are OES; camera is OES. The per-frame cost is a handful of textured quads.
- Large transparent web fallbacks allocate a bitmap per capture (~8/s); keep the layer reasonably sized.
- Prefer fewer, larger text elements over many tiny ones; text that is too small at 720p will be unreadable on phones.
- Keep chat lines short; wrapping increases layout height and can push older messages out faster.

---

## 12. Persistence and portability

The entire scene is part of `AppSettings` and serialized with kotlinx.serialization. Layer IDs are stable UUIDs; imported images are referenced by absolute internal paths. The model is pure Kotlin (`:core`), which is why the same JSON can drive an iOS implementation of the scene.

If you edit the JSON manually (not recommended), keep the sealed `SourceConfig` discriminator (`text`, `chat`, `stats`, `web`, `image`, `console`) and the `audience` enum consistent with the `type`.
