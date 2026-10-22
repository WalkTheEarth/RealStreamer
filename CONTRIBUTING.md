# Contributing

This is a personal project, but it is structured to be worked on. Before changing code, read [docs/DEVELOPING.md](docs/DEVELOPING.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Ground rules

- Keep the WYSIWYG and operator-privacy invariants: the preview must come from the same compositor output as the encoder; streamer-only sources must never be composited.
- Keep `:core` free of Android dependencies.
- Do not add vendor-specific camera code. UVC must remain generic USB Video Class.
- Never log stream keys, tokens, or full destination URLs. Use `RtmpUrl.masked()`.
- Don't add a screen-capture path.
- Document device-dependent behavior and add reproduction details to [docs/TESTING.md](docs/TESTING.md).

## Workflow

```bash
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

1. Make a focused change.
2. Add or update tests (protocol/model changes need JVM tests; UI/engine changes need the on-device checklist).
3. Run the full command above; release builds (`assembleRelease`) must still pass R8.
4. Update the relevant doc (README, USER-GUIDE, STREAMING-GUIDE, OVERLAYS, ARCHITECTURE, etc.) in the same change.
5. Describe the device(s) used if the change touches capture/GL/UVC/WebView.
6. Keep commit messages scoped and descriptive.

## Pull request expectations

- Explain the user-visible behavior and the failure modes.
- Call out any hardware verification performed (model/ROM/camera).
- Note known gaps rather than hiding them.
- Do not commit keystores, `local.properties`, build outputs, `.tooling`, or app data.

## Style

Match the surrounding Kotlin/Compose style. Prefer immutable models and `copy`. Comments explain why, especially around ordering, timestamps, and threading. Reuse existing UI components.

## Security

Open a private security advisory for sensitive reports rather than a public issue. Do not include keys/tokens in reports.
