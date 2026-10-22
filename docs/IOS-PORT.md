# iOS port guide

The app is Android-first. This document is the intended path for an iOS/iPadOS port; **no iOS target is included or was built** in this repository. It exists so the engine boundary stays honest and the work can be done without re-architecting.

---

## What is portable

- **`:core` models** (`AppSettings`, `Scene`, `SourceConfig`, `NormalizedRect`, destinations) are pure Kotlin. They can be moved to Kotlin Multiplatform shared code almost unchanged. `kotlinx.serialization` supports Kotlin/Native.
- **Scene semantics** — normalized coordinates, audience split, source ordering, token substitution — are platform-neutral.
- **The multi-destination design** — one encode, one bounded queue per destination, keyframe gating, timestamp rebasing, independent reconnect — should be preserved. It is the most important part of the app.

## What must be rewritten

| Android | iOS/iPadOS equivalent | Notes |
|---|---|---|
| Camera2 | AVFoundation `AVCaptureSession` + `AVCaptureVideoDataOutput` or `AVCaptureVideoPreviewLayer` | Use a `CVPixelBuffer` pool; feed Metal via `CVMetalTextureCache`. |
| UVC (`UVCCamera`/libusb) | AVFoundation external camera (`.external` device type) where the OS exposes it | iPadOS with USB‑C and recent iOS versions may expose UVC cameras as `AVCaptureDevice`s. **Verify on the exact target OS/device**; external camera support is limited and firmware/OS dependent. There is no equivalent to Android's USB host + libusb path. |
| EGL + GLES2 compositor | Metal render pass | One scene texture; render camera + overlays into it; present it to a `CAMetalLayer` and feed the same texture to VideoToolbox. The UV rotation/mirror math translates directly. |
| Canvas overlay bitmaps | `UIGraphicsImageRenderer` / CoreText / CoreGraphics | Render text/chat/stats into `CGImage`s and upload as Metal textures. |
| WebView virtual display | `WKWebView` + `takeSnapshot(with:completionHandler:)` or a `WKWebView` rendered into a bitmap | iOS has no app-created virtual display API. Snapshot mode only; expect lower frame rate and possible missing video/canvas content. This is a real limitation, not a porting detail. |
| MediaCodec H.264 surface encoder | VideoToolbox `VTCompressionSession` | Feed `CVPixelBuffer`s from Metal; request keyframes; read the format description for SPS/PPS; convert to AVCC. |
| MediaCodec AAC | AudioToolbox `AudioConverter` / AVAudioEngine | Produce AAC-LC and an `AudioSpecificConfig`. |
| `RtmpPublisher` (Kotlin/JVM sockets + `SSLSocket`) | Rewrite with Network.framework and TLS, or use HaishinKit | The protocol logic (AMF0, chunking, FLV, ACKs, timestamp rebasing) can be translated 1:1. HaishinKit already provides RTMP publishing; the fan-out wrapper still needs to be written. |
| Foreground service + camera/mic FGS | No direct equivalent | iOS does not allow indefinite background camera/mic capture. A normal app is suspended in the background; IRL streaming apps typically must remain in the foreground (screen on). ReplayKit broadcast extensions capture the **screen**, not an arbitrary camera composite, so it is not a substitute for this architecture. Plan the product around foreground streaming. |
| Compose UI | SwiftUI (or Compose Multiplatform for iOS) | The screens, theme, and console panels can be reproduced. The overlay editor maps naturally to gestures + a Metal-backed preview. |
| Android USB permission flow | None | iOS camera permission is a normal `AVCaptureDevice.requestAccess`. |

## Suggested order

1. Port `:core` to a KMP module; serialize the same `AppSettings` JSON so scenes are portable.
2. Build a Metal preview + camera capture pipeline and a WYSIWYG scene renderer; verify rotation/mirror on the target hardware.
3. Add VideoToolbox H.264 and AudioToolbox AAC, then a single-destination RTMP publisher (or HaishinKit). Validate against a local RTMP server with ffprobe, as the Android core test does.
4. Add the fan-out layer with per-destination queues/reconnect and timestamp rebasing.
5. Add text/chat/stats overlays (Canvas/CoreGraphics to Metal), then the WKWebView snapshot source.
6. Rebuild the operator console (SwiftUI) and overlay editor.
7. External (UVC) camera: test AVFoundation external device enumeration on iPadOS/USB‑C. If the target OS does not expose the camera, there is no supported workaround comparable to Android's libusb UVC stack on a non-jailbroken device.

## Product caveats

- Background streaming is the biggest platform difference. Android's foreground service keeps capture alive with the screen off; iOS generally cannot (outside of supported background audio sessions, which do not cover camera capture indefinitely). A "screen off IRL" feature would need a different product design.
- App Store review may scrutinize background camera/mic usage and external camera accessory access. Declare only the entitlements/usage descriptions you actually use.
- UVC camera support on iOS/iPadOS is the second biggest risk. Treat it as a research spike before committing to the port.
- The web-source feature will be snapshot-only on iOS. If live web overlays are essential, that needs to be communicated up front.

The Android engine is deliberately isolated behind `StreamEngine` / `VideoCompositor` / `Camera2Controller` / `UvcController` interfaces so the Android-specific work does not leak into the scene model or the UI. Keep the same boundary in the iOS port.
