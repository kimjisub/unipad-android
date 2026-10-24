#!/usr/bin/env python3
"""Encode one isolated 440 Hz tone per speaker into quad and 5.1 AAC fixtures."""
import array
import math
from pathlib import Path
import subprocess
import tempfile

RATE = 44100
FRAMES_PER_SPEAKER = RATE // 2
DEST = Path(__file__).resolve().parents[1] / 'app/src/androidTest/assets/audio-multichannel'


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        for channels, layout, name in [(4, 'quad', 'quad'), (6, '5.1', 'surround')]:
            samples = array.array('h')
            for frame in range(FRAMES_PER_SPEAKER * channels):
                speaker = frame // FRAMES_PER_SPEAKER
                sample = round(6553 * math.sin(2 * math.pi * 440 * frame / RATE))
                samples.extend(sample if channel == speaker else 0 for channel in range(channels))
            raw = Path(temporary) / 'input.pcm'
            with raw.open('wb') as output:
                samples.tofile(output)
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(RATE),
                            '-channel_layout', layout, '-i', str(raw), '-c:a', 'aac',
                            '-b:a', '384k', '-aac_pce', '1' if channels == 4 else '0', '-fflags', '+bitexact',
                            '-flags:a', '+bitexact', '-map_metadata', '-1',
                            str(DEST / f'{name}.m4a')], check=True)


if __name__ == '__main__':
    main()
