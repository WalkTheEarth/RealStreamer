# ADR-0007: TextureView preview consumer for Compose interop

- **Status:** Accepted
- **Date:** 2026-10-22
- **Supersedes:** [ADR-0004](0004-surfaceview-with-compose-overlays.md)

## Context

ADR-0004 chose a `SurfaceView` for zero-copy preview presentation. On a Samsung Galaxy S23 FE (SM-S711B, Android 16), the camera delivered 30 fps, the compositor logged its first frame and swaps, and the SurfaceView reported a valid surface and size — but the preview stayed black. The SurfaceView was nested in a `FrameLayout` inside a Compose `AndroidView`; its window layer did not resolve through the Compose render node. This reproduced across front and rear cameras and blocked the core use case.

The preview only needs to display the compositor's scene at phone resolution. The encoder still receives its own Surface. A TextureView adds one GPU copy through the view system, but it draws as part of the normal window and Compose content can be layered above it.

## Decision

Use a `TextureView` inside the existing `FrameLayout`/`AndroidView` interop wrapper. Wrap its `SurfaceTexture` in a `Surface` and hand that Surface to the compositor's EGL preview output. Mark the view non-clickable/non-focusable so Compose controls and overlay handles receive touches. On `onSurfaceTextureDestroyed`, detach the compositor first, release the Surface wrapper, then return `true`. On size changes, recreate the preview EGL surface. Keep the single-render, two-blit model (preview + encoder).

Two implementation rules are part of the decision:

- `present()` normalizes its fullscreen quad against the **preview EGL surface** dimensions, not the 1280x720 FBO; otherwise the preview appears in the lower-left quarter.
- Camera rotation uses the **physical DisplayManager rotation** consistently in the open path and the display listener. `context.display.rotation` and DisplayManager can disagree while the activity is locked to landscape; mixing them produced a sideways transform a frame after each open.

## Consequences

- The preview is visible and correctly sized for built-in and UVC cameras across camera switches.
- Compose controls/handles remain above the video; the overlay editor needs no native view.
- One extra GPU copy at preview resolution; the encoder path is unchanged.
- The compositor still owns no preview view; it borrows the Surface, and the host owns the TextureView lifecycle.
- A future `AndroidExternalSurface` or platform/ROM fix could restore a direct SurfaceView path. Any such change must be validated on the Samsung/Android 16 case and the full preview checklist before replacing this decision.

## Alternatives rejected

- **Keep SurfaceView** — black preview on the test device; not shippable.
- **`AndroidExternalSurface`** — a promising Compose-native API, but not evaluated on the target at the time; revisit with a device matrix.
- **Render Compose UI into the GL scene** — defeats WYSIWYG/privacy separation.
- **Software bitmap copy** — unnecessary CPU cost and latency.
