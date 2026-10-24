#!/usr/bin/env python3
"""Generate original one-second PCM references and the fixed screen-test pack.

Requires ffmpeg only for fixture authoring, never for measuring app playback.
The checked-in manifest pins the actual encoded bytes and records every command.
"""
import array
import hashlib
import json
import math
import struct
from pathlib import Path
import subprocess
import sys
import wave

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/androidTest/assets/audio-output'
REFS = ROOT / 'scripts/fixtures/audio-output'
NAMES = ['pcm.wav', 'mpeg.mp3', 'vorbis.ogg', 'aac.m4a', 'bare-named.wav',
         'tagged-named.wav', 'left.wav', 'right.wav', 'silence.wav', 'loop.wav']


def tone(pad):
    hz = 331 + 137*pad
    pcm = array.array('h')
    for i in range(48000):
        t = i/48000
        fade = min(1., i/480, (47999-i)/480)
        envelope = fade * (.4 + .6 * ((i//1384) % 3 != 0))
        for channel in range(2):
            value = .16 * envelope * math.sin(2*math.pi*((hz+channel*61)*t+37*t*t))
            if pad == 8 or (pad == 6 and channel == 1) or (pad == 7 and channel == 0):
                value = 0.
            pcm.append(round(value*32767))
    return pcm


def multichannel(manifest):
    """Pin distinct stereo references and source speaker layouts without invoking an encoder."""
    manifest['pads'] = [p for p in manifest['pads'] if p['pad'] < 10]
    for pad, name, channels, mask in [(10, 'quad.wav', 4, 0x33), (11, 'surround.wav', 6, 0x3f)]:
        pcm = tone(pad)
        source = REFS / f'pad-{pad}.wav'
        samples = array.array('h', pcm)
        if sys.byteorder == 'big': samples.byteswap()
        with wave.open(str(source), 'wb') as out:
            out.setnchannels(2); out.setsampwidth(2); out.setframerate(48000); out.writeframes(samples.tobytes())
        interleaved = array.array('h')
        for left, right in zip(pcm[::2], pcm[1::2]):
            # Quad averages each front/rear pair; 5.1 folds surrounds at sqrt(0.5).
            frame = [left, right, left, right] if channels == 4 else [0, 0, 0, 0, round(left/math.sqrt(.5)), round(right/math.sqrt(.5))]
            interleaved.extend(frame)
        if sys.byteorder == 'big': interleaved.byteswap()
        data = interleaved.tobytes()
        fmt = struct.pack('<HHIIHHHHI', 0xfffe, channels, 48000, 48000*channels*2, channels*2, 16, 22, 16, mask)
        fmt += bytes.fromhex('0100000000001000800000aa00389b71')
        target = ASSETS/name
        target.write_bytes(b'RIFF'+struct.pack('<I', 60+len(data))+b'WAVEfmt '+struct.pack('<I', 40)+fmt+b'data'+struct.pack('<I', len(data))+data)
        manifest['pads'].append({'pad':pad, 'file':name, 'reference':str(source.relative_to(ROOT)),
                                'frequencies_hz':[331+137*pad, 392+137*pad], 'silent_channels':[],
                                'source_channels':channels, 'wave_channel_mask':mask,
                                'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
                                'reference_sha256':hashlib.sha256(source.read_bytes()).hexdigest()})


def write_mapping(manifest):
    (ASSETS/'keySound').write_text(''.join(f"1 {p['pad']//8+1} {p['pad']%8+1} {p['file']}"+
                                          (' 0' if p['pad']==9 else '')+'\n' for p in manifest['pads']))
    manifest['pack_files'] = {name:hashlib.sha256((ASSETS/name).read_bytes()).hexdigest()
                              for name in ('info','keySound')}
    (REFS/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    REFS.mkdir(parents=True, exist_ok=True)
    if sys.argv[1:] == ['--multichannel-only']:
        manifest = json.loads((REFS/'manifest.json').read_text())
        multichannel(manifest)
        write_mapping(manifest)
        return
    manifest = {'version': 1, 'sample_rate': 48000, 'seconds': 1,
                'ffmpeg_version': subprocess.check_output(['ffmpeg','-version'],text=True).splitlines()[0],
                'pads': [], 'commands': []}
    for pad,name in enumerate(NAMES):
        hz = 331 + 137*pad
        pcm = tone(pad)
        if sys.byteorder == 'big': pcm.byteswap()
        source = REFS / f'pad-{pad}.wav'
        with wave.open(str(source),'wb') as out:
            out.setnchannels(2); out.setsampwidth(2); out.setframerate(48000); out.writeframes(pcm.tobytes())
        target = ASSETS/name
        if pad in (1,2,3,4,5):
            codec = {1:['-c:a','libmp3lame','-b:a','192k','-write_xing','0','-id3v2_version','0','-f','mp3'],
                     2:['-c:a','vorbis','-strict','experimental','-q:a','6','-f','ogg'],
                     3:['-c:a','aac','-b:a','192k','-f','ipod'],
                     4:['-c:a','libmp3lame','-b:a','192k','-write_xing','0','-id3v2_version','0','-f','mp3'],
                     5:['-c:a','libmp3lame','-b:a','192k','-write_xing','0','-id3v2_version','3',
                        '-metadata','title=Original output fixture','-f','mp3']}[pad]
            command = ['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(source.relative_to(ROOT)),
                       '-map_metadata','-1',*codec,str(target.relative_to(ROOT))]
            subprocess.run(command, cwd=ROOT,check=True)
            manifest['commands'].append(command)
        else:
            target.write_bytes(source.read_bytes())
        manifest['pads'].append({'pad':pad,'file':name,'reference':str(source.relative_to(ROOT)),
                                'frequencies_hz':[hz,hz+61], 'silent_channels':
                                [0,1] if pad == 8 else [1] if pad == 6 else [0] if pad == 7 else [],
                                'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
                                'reference_sha256':hashlib.sha256(source.read_bytes()).hexdigest()})
    (ASSETS/'info').write_text('title=Original output signals\nproducerName=UniPad tests\nbuttonX=8\nbuttonY=8\nchain=1\nsquareButton=true\n')
    multichannel(manifest)
    write_mapping(manifest)


if __name__ == '__main__': main()
