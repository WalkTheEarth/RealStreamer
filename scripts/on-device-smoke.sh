#!/usr/bin/env bash
# Installs the debug app and streams relevant logcat tags.
# Usage: ./scripts/on-device-smoke.sh [apk-path]
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required." >&2
  exit 1
fi
if [ ! -f "${APK}" ]; then
  echo "APK not found: ${APK}" >&2
  echo "Run: ./gradlew :app:assembleDebug" >&2
  exit 1
fi

adb install -r "${APK}"
adb shell am start -n com.irlstream.app.debug/com.irlstream.app.MainActivity
adb logcat -c
echo "Streaming logs (Ctrl+C to stop)..."
exec adb logcat -s \
  StreamEngine VideoCompositor VideoEncoder AudioEngine Camera2Controller \
  UvcController WebOverlayManager StreamingService RtmpPublisher
