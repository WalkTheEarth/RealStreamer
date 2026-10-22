# Security and privacy

This document describes what RealStreamer stores, what it sends, and what it deliberately does not protect. Read it before streaming from a shared or untrusted device.

---

## 1. Data inventory

| Data | Where | Encrypted at rest? | Backed up? | Logged? |
|---|---|---|---|---|
| Scene, sources, video/audio/camera settings | `filesDir/realstreamer-settings.json` | No | No (explicitly excluded) | No |
| Destination names + full RTMP/RTMPS URLs incl. **stream keys** | same JSON | No | No | Only masked URL in errors/UI |
| Twitch OAuth token, account name | same JSON | No | No | No |
| Custom chat WebSocket URL + field mapping | same JSON | No | No | No |
| Imported overlay images | `filesDir/overlays/*.img` | No | No | No |
| Chat messages | in memory only | n/a | No | No |
| Encoded media | in memory/socket | TLS if `rtmps` | n/a | No |
| App-private files | Android sandbox | OS-level file-based encryption on modern Android | Excluded from cloud/device transfer | n/a |

The settings file is private to the app's sandbox. On a rooted device, or via a full-device backup tool that ignores app rules, it is readable. There is **no application-level encryption**: do not treat this as a secret vault.

Backup exclusions are enforced with `android:allowBackup="false"`, `android:dataExtractionRules` (Android 12+), and `fullBackupContent` (older). See `app/src/main/res/xml/`.

---

## 2. Permissions

| Permission | Why | When |
|---|---|---|
| `CAMERA` | Preview + built-in capture | First studio entry |
| `RECORD_AUDIO` | Mic capture | First studio entry / Go Live |
| `INTERNET` | RTMP, chat, web | Always |
| `ACCESS_NETWORK_STATE` | Declared for future/network diagnostics; not required for current behavior | — |
| `POST_NOTIFICATIONS` | Live foreground notification | Android 13+ |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_MICROPHONE` | Screen-off streaming | Manifest; service start |
| `WAKE_LOCK` | Keep the CPU alive while publishing | Live only |

USB camera access uses the Android **USB permission dialog** per device; it is not a manifest permission. RealStreamer registers its USB receiver with `RECEIVER_NOT_EXPORTED` and requests permission with a package-scoped mutable `PendingIntent`.

There is no storage permission; images are imported through the system photo picker.

---

## 3. Network behavior

RealStreamer has **no backend, no analytics, no ads, no crash reporting, and no remote config**.

Outbound connections:

1. **RTMP/RTMPS destinations** you configure. `rtmps://` uses TLS with HTTPS-style endpoint identification. Plain `rtmp://` is unencrypted on the internet — use RTMPS where available.
2. **Twitch IRC** over `wss://irc-ws.chat.twitch.tv:443` (TLS) when a Twitch channel is configured.
3. **Custom chat WebSocket** — `ws://` or `wss://` exactly as configured. Prefer `wss`.
4. **Web sources** — the page and all subresources it requests, from the WebView. The page can contact third parties. `usesCleartextTraffic` is enabled so http widget URLs load; prefer https.
5. **DNS/system services** used by the above.

Cleartext HTTP is allowed only for WebView content and plain RTMP; the app does not use HTTP APIs.

### Stream keys

A full ingest URL contains a secret. RealStreamer:

- masks the stream name after the app path in the destination card and edit field (length-preserving bullets; reveals only through the eye toggle);
- masks it in `RtmpUrl.masked()` used in errors and logs;
- never writes it to logcat;
- stores it only in the app-private settings file.

If a key is exposed (screen recording, shoulder-surfing, pasted into a chat), rotate it in the platform dashboard.

---

## 4. WebView and web sources

- JavaScript, DOM storage, media autoplay, cookies, and mixed-content compatibility are enabled. These are necessary for widgets and dashboards.
- A web source is arbitrary code from the URL you choose. It can make network requests, fingerprint the device/network, and (within WebView's sandbox) attempt to interact with the page. It does **not** get access to the app's settings, stream keys, camera, or microphone.
- The WebView is offscreen and not interactively browsable from the studio.
- Snapshot mode draws the page to a bitmap; the page is not granted any additional Android permission.
- Only load pages you trust. Prefer self-hosted or well-known widget providers.

There is no ad-blocking or script blocking. If you need isolation, host the widget yourself.

---

## 5. USB camera and capture

- UVC video is read through the Android USB host stack using the vendored libusb/libuvc/UVCCamera stack. It has no network access.
- The USB device permission is scoped to the app and the device; it can be revoked in Android settings or by replugging.
- Camera frames are never written to disk by the app. They exist in GPU textures and encoded packets.
- Audio is read from the phone microphone and AAC-encoded in memory. No audio is recorded locally.
- There is no local recording feature; if you add one, it creates a new at-rest data category that this document must cover.

---

## 6. Background capture and the foreground service

- Capture while backgrounded/screen-off runs under a foreground service with `camera|microphone` types and an ongoing notification. Android shows the camera/mic indicators; that is expected.
- The service holds a partial wake lock with a 12-hour safety cap; it is released on stop/destroy.
- If the service is stopped by the system while live, the engine ends the stream rather than leaving capture running.
- If a stream is stopped from the notification while no preview is attached, the engine closes the camera/mic and releases the compositor. It does not keep capture alive without a visible/ongoing session.
- OEM battery optimizations can still kill background capture; allow RealStreamer to run in the background if you need screen-off streaming.

---

## 7. Threat model

### Protected (by design)

- **Streamer console privacy** — the sidebar is Compose UI; there is no path that composites it into the encoder. This is structural, not a "don't show" toggle.
- **Key disclosure in UI/logs** — masked display and masked error URLs; no key logging.
- **Backup leakage** — settings excluded from cloud backup and device transfer.
- **Cleartext on untrusted networks** — RTMPS supported with hostname verification.
- **Accidental screen capture** — no MediaProjection/screen-capture pipeline exists; overlays are a GPU scene.
- **Web page access to app secrets** — WebView cannot read the settings file or stream keys.
- **Leaving the camera on when idle** — backgrounding without a live stream stops capture; stopping a backgrounded stream releases it.

### Not protected

- **At-rest encryption** of the settings file. A rooted device or a forensic image can read keys/tokens.
- **Malicious platform/RTMP server** — a server you configure receives your stream; use trusted ingests.
- **Malicious WebView content** — it runs with JS and can track the device/network, though not read app secrets.
- **Shoulder surfing / screen recording** of the destination editor with the key revealed.
- **Network-level metadata** — an observer can see connections to RTMP/chat hosts even under TLS.
- **OEM/OS compromise** — a compromised OS can read anything the app can.
- **Stream key validation** — the app does not verify the server identity beyond TLS hostname checks; plain RTMP has no server authentication.

### Hardening checklist

- Use `rtmps://` destinations and `wss://` chat where offered.
- Use a dedicated stream key per service; rotate if exposed.
- Don't reveal keys on stream or in screenshots.
- Keep Android and the app updated; install release builds from a trusted source.
- Avoid untrusted web-source URLs.
- On a shared device, uninstall/clear app data before handing it over.
- Consider a device-level work profile or a dedicated streaming phone for high-value keys.
- Verify the on-stream preview never contains the streamer sidebar before a public stream (the checklist includes this).

---

## 8. What to do if a key leaks

1. End the stream.
2. Reset/rotate the stream key in the platform dashboard (Twitch/YouTube/Kick).
3. Update the destination URL in RealStreamer (it will reconnect).
4. If a device was lost, remove the app/authorize new keys; disable the old key server-side. RealStreamer has no remote wipe.

---

## 9. Reporting

This is a personal project; there is no bug bounty or dedicated security contact. If the repository is published on GitHub, open a private security advisory for sensitive reports rather than a public issue. Do not include stream keys or tokens in reports.
