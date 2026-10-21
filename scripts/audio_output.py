#!/usr/bin/env python3
"""Versioned, dependency-free matched-waveform verdict for emulator output PCM.

Only recordings made outside the app count. A request, file size or nonzero RMS
alone is insufficient. See AUDIO_OUTPUT.md for the capture and timing contract.
"""
from __future__ import annotations

import array
import cmath
import math
from pathlib import Path
import sys
import wave

VERSION = 1
RATE = 24000
DISCARD_S = .2
MIN_RMS_DBFS = -50.
MIN_RISE_DB = 20.
MIN_CORRELATION = .92
SILENCE_DBFS = -65.
MAX_OUTPUT_DELAY_S = .5


def read_wav(path: Path, rate: int = RATE) -> list[list[float]]:
    """Box-filter before decimation; both reference and capture use the same filter."""
    with wave.open(str(path), 'rb') as wav:
        if wav.getsampwidth() != 2 or wav.getnchannels() != 2:
            raise ValueError('16-bit stereo PCM is required')
        source_rate = wav.getframerate()
        if source_rate % rate:
            raise ValueError(f'unsupported sample rate {source_rate}; no silent rate conversion')
        step = source_rate // rate
        pcm = array.array('h', wav.readframes(wav.getnframes()))
    if sys.byteorder == 'big':
        pcm.byteswap()
    return [[sum(pcm[i+ch:i+2*step:2]) / (32768 * step)
             for i in range(0, len(pcm)-2*step+1, 2*step)] for ch in range(2)]


def levels(values: list[float]) -> dict:
    def db(value):
        return 20 * math.log10(value) if value else None
    return {'rms_dbfs': db(math.sqrt(sum(v*v for v in values)/len(values))) if values else None,
            'peak_dbfs': db(max(map(abs, values), default=0.)), 'frames': len(values)}


def _fft(values: list[complex], inverse: bool = False) -> list[complex]:
    """Radix-two FFT, used only off device, outside the audio callback."""
    out = list(map(complex, values))
    n, j = len(out), 0
    for i in range(1, n):
        bit = n >> 1
        while j & bit:
            j ^= bit
            bit >>= 1
        j ^= bit
        if i < j:
            out[i], out[j] = out[j], out[i]
    width = 2
    while width <= n:
        root = cmath.exp((2j if inverse else -2j) * math.pi / width)
        for first in range(0, n, width):
            phase = 1+0j
            for k in range(first, first+width//2):
                a, b = out[k], out[k+width//2]*phase
                out[k], out[k+width//2] = a+b, a-b
                phase *= root
        width *= 2
    return [v/n for v in out] if inverse else out


def _match(values: list[float], template: list[float], lo: int, hi: int) -> tuple[float, int]:
    n = 1 << (len(values)+len(template)-1).bit_length()
    left = _fft(values + [0.] * (n-len(values)))
    right = _fft(template[::-1] + [0.] * (n-len(template)))
    dots = _fft([a*b for a,b in zip(left,right)], inverse=True)
    sums = [0.]
    for v in values:
        sums.append(sums[-1]+v*v)
    energy = sum(v*v for v in template)
    best = (0., lo)
    for start in range(max(0, lo), min(hi, len(values)-len(template))+1):
        denom = math.sqrt(max(0., sums[start+len(template)]-sums[start])*energy)
        score = min(1., dots[start+len(template)-1].real/denom) if denom else 0.
        if score > best[0]:
            best = score, start
    return best


def is_silent(channels: list[list[float]], rate: int, start: float, end: float) -> dict:
    metrics = [levels(c[int(start*rate):int(end*rate)]) for c in channels]
    return {'passed': bool(metrics) and start >= DISCARD_S and end > start and
            all(len(c) >= int(end*rate) for c in channels) and
            all(m['rms_dbfs'] is None or m['rms_dbfs'] <= SILENCE_DBFS for m in metrics),
            'start_s': start, 'end_s': end, 'channels': metrics}


def compare(captured: list[list[float]], reference: list[list[float]], rate: int,
            pressed: float) -> dict:
    result = {'version': VERSION, 'passed': False, 'channels': []}
    # Half a second before input, after discarded old buffers; half a second after
    # the full one-second sound, even at the maximum permitted output delay.
    if (len(captured) != 2 or len(reference) != 2 or pressed-.5 < DISCARD_S or
            any(len(c) < int((pressed+1+MAX_OUTPUT_DELAY_S+.5)*rate) for c in captured)):
        result['error'] = 'missing baseline, full sound or postroll'
        return result
    trim = int(.05*rate)
    for channel, (values, expected) in enumerate(zip(captured, reference)):
        before = levels(values[int((pressed-.5)*rate):int(pressed*rate)])
        if not any(expected):
            quiet = is_silent([values], rate, pressed, pressed+1.3)
            result['channels'].append({'channel': channel, 'silent_channel': True, **quiet})
            continue
        template = expected[trim:int(.95*rate)]
        score, start = _match(values, template, int(pressed*rate)+trim,
                              int((pressed+MAX_OUTPUT_DELAY_S)*rate)+trim)
        signal = levels(values[start:start+len(template)])
        rms = signal['rms_dbfs']
        rise = None if before['rms_dbfs'] is None or rms is None else rms-before['rms_dbfs']
        result['channels'].append({'channel': channel, 'baseline': before, **signal,
                                   'rise_db': rise, 'correlation': score,
                                   'onset_s': (start-trim)/rate,
                                   'passed': rms is not None and rms >= MIN_RMS_DBFS and
                                   (before['rms_dbfs'] is None or (rise is not None and rise >= MIN_RISE_DB)) and
                                   score >= MIN_CORRELATION})
    result['passed'] = all(c['passed'] for c in result['channels'])
    return result
