#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Generated tones only; no recordings or third-party audio.
for channels in 1 2 6; do
  ffmpeg -hide_banner -loglevel error -y -f lavfi \
    -i 'sine=frequency=440:sample_rate=48000:duration=0.301' \
    -ac "$channels" -c:a libopus -frame_duration 20 "tone-$channels.opus"
done
