# ADR-0006: The operator console is app UI, never a scene layer

- **Status:** Accepted
- **Date:** 2026-10-22

## Context

The design chart has a configurable side column with chat, stats, and dropped frames for the camera operator. A naive implementation would add these as overlays, which would appear in the broadcast. The user explicitly clarified that the column is what the streamer sees.

## Decision

Model sources with an explicit `audience`: `BROADCAST` (composited) or `STREAMER_ONLY` (local sidebar). The compositor iterates only broadcast sources; the console iterates only streamer sources. There is no visibility toggle that can move a panel between audiences, and no screen-capture path exists.

## Consequences

- Operator chat/stats are private by construction.
- The same chat source can feed both an on-stream chat layer and a private chat panel.
- The console layout is normal Compose (easy to configure, width/visibility, panels) and costs no GPU compositing.
- Users expecting OBS-style "streamer overlay docks" map naturally: the streamer-only tab is the dock configuration.

## Alternatives rejected

- **One scene with a "hidden from stream" flag:** a compositor bug or a screen-capture feature could leak it.
- **Screen capture with a cropped region:** fragile, quality loss, and MediaProjection consent.
