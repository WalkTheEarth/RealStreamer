# ADR-0005: One encode, independent per-destination queues and timestamp rebasing

- **Status:** Accepted
- **Date:** 2026-10-22

## Context

Users stream to multiple services. A separate encoder per destination would multiply CPU/thermal cost and could drift. A single blocking publish loop would let one slow server stall the others.

## Decision

Encode once. `StreamPipeline` copies immutable encoded frames to one `DestinationWorker` per destination. Each worker has its own thread, bounded queue, connection, reconnect policy, watchdog, keyframe gate, and stats. Each new connection sends the sequence headers, waits for a keyframe, and rebases timestamps so the new RTMP stream starts near 0 ms. When a queue is congested, non-key video/audio are dropped and counted; the encoder and other outputs are unaffected.

## Consequences

- CPU cost is independent of destination count.
- One destination's outage/backpressure cannot stall another.
- Upload bandwidth scales with destinations (documented prominently).
- Reconnects resume cleanly and quickly (fresh keyframe).
- Per-destination bitrate/retry/drop telemetry is available to the console.

## Alternatives rejected

- **Encoder per destination:** thermal/CPU blowup, possible visual drift.
- **Shared blocking socket loop:** head-of-line blocking.
- **Server-side restreaming/relay:** not the app's architecture; requires a service.
