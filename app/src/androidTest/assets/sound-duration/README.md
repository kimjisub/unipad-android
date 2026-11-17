# Sound duration fixtures

Generated silence, with no third-party audio. These fixtures may be used without restriction.
Each input contains 1.234 seconds of stereo silence at 44,100 Hz.

ffmpeg version 8.1 Copyright (c) 2000-2026 the FFmpeg developers

```sh
ffmpeg -f lavfi -i anullsrc=r=44100:cl=stereo -t 1.234 -c:a pcm_s16le silence.wav
ffmpeg -f lavfi -i anullsrc=r=44100:cl=stereo -t 1.234 -c:a libmp3lame silence.mp3
ffmpeg -f lavfi -i anullsrc=r=44100:cl=stereo -t 1.234 -c:a vorbis -strict -2 silence.ogg
ffmpeg -f lavfi -i anullsrc=r=44100:cl=stereo -t 1.234 -c:a aac silence.m4a
```

Encoded track duration may include codec padding. The device comparison records both
MediaPlayer and the playback extractor before changing autoplay timing.
