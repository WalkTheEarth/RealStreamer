# ADR-0001: WYSIWYG GPU compositor, not screen capture

- **Status:** Accepted
- **Date:** 2026-10-22

## Context

The app needs a live preview that exactly matches the broadcast while overlays (text, chat, web, images) are visible. Android offers MediaProjection screen capture, which would capture whatever the app draws, including the streamer console. It requires a user consent flow, captures at screen resolution, adds encode/decode latency, and makes the "sidebar is private" requirement impossible to guarantee structurally.

## Decision

Render the camera and broadcast overlays into an offscreen GL texture (the scene), then blit that single texture to both the preview SurfaceView and the MediaCodec input surface. The operator console is Compose UI outside the compositor.

## Consequences

- Preview and encoded output are guaranteed to be the same pixels (apart from display scaling).
- The sidebar is private by construction: the compositor never sees it.
- No MediaProjection consent, no screen content in the video, no extra encode/decode hop.
- We own the EGL/GLES scene, texture uploads, and camera UV transforms.
- Overlays must be rendered into textures (Canvas bitmaps, OES web textures) rather than being normal Android views.

## Alternatives rejected

- **MediaProjection screen capture:** privacy leakage of the console, consent friction, latency, and quality loss.
- **Two pipelines (preview views + capture):** WYSIWYG drift and duplicate rendering.
- **CameraX-only preview without a compositor:** no arbitrary overlays in the encoder.
