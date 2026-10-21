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

VERSION = 2
RATE = 6000
DISCARD_S = .2
MIN_RMS_DBFS = -50.
MIN_RISE_DB = 20.
MIN_CORRELATION = .90
SILENCE_DBFS = -65.
MAX_OUTPUT_DELAY_S = 1.0
OUTPUT_WINDOW_S = 1.0 + MAX_OUTPUT_DELAY_S + .5


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


def _spectrum(values: list[float]) -> list[list[float]]:
    # 42.7 ms Hann windows, 20 ms hop: retain the changing pitch and envelope,
    # without requiring a lossy decoder or streamed PCM to preserve sample phase.
    width, hop = 256, 120
    window = [.5-.5*math.cos(2*math.pi*i/(width-1)) for i in range(width)]
    return [[abs(v) for v in _fft([a*b for a,b in zip(values[start:start+width],window)])[1:width//2]]
            for start in range(0,len(values)-width+1,hop)]


def _match(values: list[float], template: list[float], lo: int, hi: int) -> tuple[float, int]:
    # Both sides use the same short-time Fourier magnitude representation. A gap
    # in the middle, another pitch, a click, a missing chord note or swapped
    # channels still disagrees across the full 0.9-second reference.
    hop = 120
    lo = max(0,lo)
    last = min(hi,len(values)-len(template))
    if last < lo: return 0.,lo
    reference = _spectrum(template)
    actual = _spectrum(values[lo:last+len(template)])
    energy = sum(v*v for frame in reference for v in frame)
    best = (0.,lo)
    for offset in range((last-lo)//hop+1):
        frames = actual[offset:offset+len(reference)]
        observed = sum(v*v for frame in frames for v in frame)
        dot = sum(a*b for ref,seen in zip(reference,frames) for a,b in zip(ref,seen))
        score = min(1.,dot/math.sqrt(energy*observed)) if energy*observed else 0.
        if score > best[0]: best = score,lo+offset*hop
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
            any(len(c) < int((pressed+OUTPUT_WINDOW_S)*rate) for c in captured)):
        result['error'] = 'missing baseline, full sound or postroll'
        return result
    trim = int(.05*rate)
    for channel, (values, expected) in enumerate(zip(captured, reference)):
        before = levels(values[int((pressed-.5)*rate):int(pressed*rate)])
        if not any(expected):
            quiet = is_silent([values], rate, pressed, pressed+OUTPUT_WINDOW_S)
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
