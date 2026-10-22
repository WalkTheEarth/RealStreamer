# FAQ

### Is there an iPhone / iPad version?

Not built. The repository is an Android app; the intended port path is documented in [IOS-PORT.md](IOS-PORT.md). In particular, iOS has no equivalent to Android's USB host UVC stack, so external cameras depend on whether the target OS exposes them through AVFoundation.

### Does it support the DJI Action 4?

Yes, as a **generic UVC webcam**. Put the Action 4 in Webcam/USB mode and connect it with a data-capable cable/adapter. There is no DJI-specific code or SDK. Audio comes from the phone microphone, not the camera.

### Can I choose a specific rear or front camera?

Yes. RealStreamer enumerates every camera the Android camera service exposes, including multiple rear lenses and duplicate logical cameras. The camera sheet and Settings → Camera list each ID with its focal length and maximum resolution. Pick one; the selection is saved. The default for a lens is the lowest camera ID that faces that way. The FLIP control still toggles between front and back.

### The preview is wider or narrower after switching cameras. Why?

The phone may expose a main, ultrawide, telephoto, or depth-adjacent camera as separate IDs. Each has a different field of view; RealStreamer center-crops the sensor frame to the selected stream aspect, so the field of view changes with the lens even though the output resolution does not.

### Can I stream to Twitch, YouTube, and Kick at the same time?

Yes. Add each destination with its full ingest URL and key. One encode is fanned out to independent connections. Keep in mind that each destination consumes its own upload bandwidth, and check each platform's simulcasting rules.

### How much upload bandwidth do I need?

Roughly `(video bitrate + audio bitrate) × destinations × 1.08`, keeping total usage under 60–70% of your measured uplink. See [STREAMING-GUIDE.md](STREAMING-GUIDE.md) for a table.

### Does streaming continue with the screen off?

Yes, while the stream is live and the foreground service is running. Start the stream from the studio so Android accepts the camera/mic foreground service. Aggressive OEM battery managers may still kill it; allow RealStreamer to run in the background.

### Is the sidebar streamed to viewers?

No. The streamer console is Compose UI. The compositor only reads `scene.broadcastSources`; streamer-only sources are never part of the scene texture. The UI says **LOCAL ONLY**, and the on-device checklist verifies it against a recording.

### Can viewers see the overlay selection boxes?

No. Those handles are Compose UI above the preview TextureView, not compositor layers.

### Can I add chat to the stream and also have a private chat reader?

Yes. Add **Chat overlay** under Overlays → On stream for viewers and a **Chat** panel under Streamer only for yourself. They share the same chat source.

### Which chat platforms are supported?

Twitch IRC (anonymous or OAuth), a generic JSON WebSocket, and local demo messages. YouTube/Kick/TikTok chat is not built in; a relay that exposes their chat over a WebSocket can be used as the custom source.

### Are emotes/images shown in chat?

No. Messages are text; emote codes appear as their text names. Names use the platform's color when available.

### Can I use a USB microphone or the camera's audio?

No. Audio is captured from the phone's built-in microphone (with optional AEC/NS/AGC). The bundled UVC stack does not expose UAC audio.

### Can I record locally?

No. There is no MP4 recording feature in this version. You can record a destination locally with FFmpeg (`scripts/local-rtmp.sh`) or use the platform's VOD.

### Does it support SRT / RTSP / WebRTC ingest?

No. RTMP and RTMPS only, as destinations.

### Why doesn't it use OBS / screen capture?

Screen capture would include the operator sidebar, add a MediaProjection permission, degrade quality, and break WYSIWYG. RealStreamer composites the scene on the GPU and feeds the same pixels to preview and encoder. See [ADR-0001](adr/0001-compositor-not-screen-capture.md).

### Why not use FFmpeg for RTMP?

Size, dependencies, and testability. The in-house client is a few hundred lines of Kotlin with TLS, and it is verified against a real FFmpeg RTMP server. See [ADR-0002](adr/0002-custom-rtmp-client.md).

### Why does the app lock orientation?

The stream has a fixed orientation. Locking the activity to it keeps the camera crop and preview predictable. Other screens rotate freely.

### Why does 720p look soft on a 1080p stream (or vice versa)?

The encoder outputs the selected resolution; the preview is letterboxed to that aspect. A UVC camera may negotiate a lower size than requested (e.g., 640×480 fallback), and MJPEG at 1080p may be softer than expected. Check the camera chip label and the encoded output in a local recording.

### How many destinations can I add?

There is no hard-coded limit. CPU is mostly unaffected by destination count (the encode is shared); network and sockets are the limits. Each destination has a queue and thread.

### What happens if one destination drops?

Only that destination reconnects, with exponential backoff. Others continue. On reconnect it receives the sequence headers and waits for a keyframe; timestamps restart near zero for that connection.

### What happens when the UVC camera is unplugged?

It closes. If **Fall back to built-in camera** is on, the built-in camera opens and the stream continues (with a brief freeze). Otherwise the stream shows the last frame and an error.

### Can I use two identical USB cameras?

Only one camera source is active. Devices are identified by vendor/product ID; two identical cameras without serial support are ambiguous and the first enumerated is selected. Use one at a time.

### Where are my stream keys stored?

In app-private storage (`filesDir/realstreamer-settings.json`), unencrypted, masked in the UI, and excluded from backup. See [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md).

### Can I edit the scene while live?

Yes. Scene/overlay/camera/chat/destination changes apply immediately. Video/audio quality changes are saved and apply on the next stream.

### The overlay handles don't appear on my device.

The preview is a TextureView precisely because SurfaceView interop was unreliable on some ROMs. If handles still do not appear, the layer list still allows selection, reorder, visibility, and property editing. Please add the device/ROM to the testing notes.

### The web source is blank.

The page may block embedding, require DRM, use pop-ups, or be too heavy for a WebView. Transparent sources use snapshot mode at ~8 fps; video/canvas content may not appear. Test the URL in a browser, try an opaque page, or host a simpler widget.

### Does the app work in an emulator?

It compiles and installs, but the app targets ARM ABIs and needs a hardware H.264 encoder plus camera/UVC/GL support. A standard x86 emulator is not a supported runtime. Use a physical device for the checklist.

### Why is the app called RealStreamer but the package `com.irlstream.app`?

The product/launcher name is RealStreamer; the Android package namespace is a historical identifier kept for continuity. It does not appear in the UI. See [BRANDING.md](BRANDING.md).
