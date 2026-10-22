#!/usr/bin/env bash
# Starts an RTMP listener that records one stream to a local FLV file.
# Usage: ./scripts/local-rtmp.sh [port] [output.flv] [bind-host]
set -euo pipefail

PORT="${1:-19350}"
OUT="${2:-/tmp/irlstream-local-${PORT}.flv}"
HOST="${3:-0.0.0.0}"

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required. Install it (e.g. apt install ffmpeg)." >&2
  exit 1
fi

LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
if [ -n "${LAN_IP:-}" ]; then
  echo "Listening on rtmp://${HOST}:${PORT}/live/test"
  echo "Add this destination in RealStreamer: rtmp://${LAN_IP}:${PORT}/live/test"
else
  echo "Listening on rtmp://${HOST}:${PORT}/live/test"
fi
echo "Recording to: ${OUT}"
echo "Press Ctrl+C to stop."

ffmpeg -hide_banner -loglevel warning -listen 1 \
  -i "rtmp://${HOST}:${PORT}/live/test" \
  -c copy -y "${OUT}"

echo "Stream closed. Wrote ${OUT}"
if command -v ffprobe >/dev/null 2>&1; then
  ffprobe -hide_banner -v error -show_entries stream=codec_type,codec_name,width,height \
    -of default=noprint_wrappers=1 "${OUT}" || true
fi
