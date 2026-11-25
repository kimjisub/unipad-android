# Ogg Opus end trimming fixtures

`bash generate.sh` creates mono, stereo and 5.1 440 Hz tones using FFmpeg's
sine source and libopus. No recordings or third-party sounds are included.
Each tone has 14,448 audible frames at 48 kHz (0.301 seconds). The last Ogg
granule includes pre-skip but excludes packet padding. MediaCodec may return
that padding; only the decoded tail must be removed, never the beginning.
