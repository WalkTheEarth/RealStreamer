# ADR-0004: SurfaceView preview with Compose UI above it

- **Status:** Accepted
- **Date:** 2026-10-22

## Context

The compositor needs a preview surface that is efficient and can be layered with Compose controls and overlay-editing handles. A TextureView would support view clipping/transforms but adds a texture copy through the view system and makes layering straightforward only for Compose content; SurfaceView places its buffer beneath the window, so normal window content can appear above it.

## Decision

Use a `SurfaceView` owned by an `AndroidView`; pass its `Surface` to the compositor's EGL preview output. Mark the SurfaceView non-clickable/non-focusable so Compose controls and the overlay editor receive touches. Draw studio controls, gradients, and selection handles as normal Compose content above it.

## Consequences

- Efficient direct presentation; no extra TextureView copy.
- Compose controls/handles can cover the video, and the overlay editor no longer needs a custom native view.
- SurfaceView content cannot be clipped to rounded corners by the view system; the preview is square (the design chart is square).
- Interop edge cases exist on unusual ROMs; the layer list remains a fallback for editing.

## Alternatives rejected

- **TextureView:** extra copy, and Compose overlays require careful z-order handling anyway; the custom native editor view was more code.
- **Drawing Compose into the GL scene:** would composite the console/UI, defeating the privacy and WYSIWYG separation.
