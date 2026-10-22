# Architecture decision records

Short records of the decisions that shaped RealStreamer. Each states the context, the decision, its consequences, and what was rejected.

| ADR | Decision |
|---|---|
| [0001](0001-compositor-not-screen-capture.md) | WYSIWYG GPU compositor instead of screen capture |
| [0002](0002-custom-rtmp-client.md) | In-house RTMP publisher instead of FFmpeg/librtmp |
| [0003](0003-generic-uvc.md) | Generic UVC via an Apache-2.0 USB stack, no vendor SDKs |
| [0004](0004-surfaceview-with-compose-overlays.md) | SurfaceView preview with Compose UI above it |
| [0005](0005-single-encode-fanout.md) | One encode, independent per-destination queues and timestamp rebasing |
| [0006](0006-streamer-console-outside-compositor.md) | The operator console is app UI, never a scene layer |

ADRs are historical. If a decision changes, add a new ADR that supersedes it rather than rewriting the old one.
