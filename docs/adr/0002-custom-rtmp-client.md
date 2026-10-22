# ADR-0002: In-house RTMP client instead of FFmpeg/librtmp

- **Status:** Accepted
- **Date:** 2026-10-22

## Context

The app must publish one encoded H.264/AAC stream to multiple destinations, with independent reconnect, backpressure, keyframe gating, RTMPS, and testability. Available options were FFmpeg/ffmpeg-kit (large, partly retired binaries), librtmp JNI (small but opaque, per-connection native state), or implementing the protocol.

## Decision

Implement RTMP 1.0 and FLV muxing in pure Kotlin in `:core`: simple handshake, chunk stream reader/writer, AMF0, connect/publish commands, acknowledgements, ping/pong, chunk-size negotiation, extended timestamps, AVC/AAC FLV tagging, and TLS via `SSLSocket` with hostname verification. Validate it with a JVM integration test against a real FFmpeg RTMP listener.

## Consequences

- No native RTMP dependency, no extra ABIs, no FFmpeg licensing/size.
- The same publisher runs on a JVM for tests and is portable to Kotlin/Native.
- We own protocol bugs, but they are testable and visible; the FFmpeg integration test proves the common path.
- RTMPS and per-connection state are first-class.
- No RTMP cuing features, enhanced-RTMP/HEVC, or exotic server quirks are supported yet.

## Alternatives rejected

- **FFmpeg/ffmpeg-kit:** large binaries, uncertain maintenance, harder multi-connection control.
- **librtmp JNI:** opaque native state and ABI packaging; harder to test on the JVM.
- **HaishinKit (iOS-only):** not applicable to Android.
