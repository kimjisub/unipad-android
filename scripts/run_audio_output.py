#!/usr/bin/env python3
"""Drive real pads while devices.py records the lent emulator's external output.

The caller rents an audio-enabled emulator and returns it. This command never
starts devices, reads authentication files or plays a sound from the recorder.
Each invocation creates one immutable evidence round; archive it before a rerun.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile

from audio_output import RATE, VERSION, compare, is_silent, read_wav

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.kimjisub.launchpad.dev'
CLASS = 'com.kimjisub.launchpad.audio.EmulatorOutputScreenTest#drivesScreenForExternalOutput'
CASES = [
    ('wavIsHeardAtTheEmulatorOutput','0',[0],False),
    ('mp3IsHeardAtTheEmulatorOutput','1',[1],False),
    ('oggIsHeardAtTheEmulatorOutput','2',[2],False),
    ('m4aIsHeardAtTheEmulatorOutput','3',[3],False),
    ('bareMp3NamedWavIsHeardAtTheEmulatorOutput','4',[4],False),
    ('taggedMp3NamedWavIsHeardAtTheEmulatorOutput','5',[5],False),
    ('leftChannelIsHeardSeparately','6',[6],False),
    ('rightChannelIsHeardSeparately','7',[7],False),
    ('unpressedPadIsRejected','none',[0],True),
    ('emptyPadIsRejected','empty',[0],True),
    ('silentFileIsRejected','8',[0],True),
    ('differentPadIsRejected','1',[0],True),
    ('heldLoopStopsAfterRelease','9',[9],False),
    ('twoPadsAreHeardTogether','chord',[0,1],False),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--devices-tool', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--test-apk', required=True, type=Path)
    parser.add_argument('--block-playback', action='store_true')
    parser.add_argument('--case', action='append', choices=[c[0] for c in CASES])
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()):
        parser.error('output folder must be empty; preserve and attach the previous round first')
    command_log = []

    def run(command, *, timeout=30, check=True, input=None):
        started = time.time()
        result = subprocess.run(list(map(str,command)), input=input, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=timeout)
        command_log.append({'command':list(map(str,command)), 'epoch_s':started,
                            'exit':result.returncode, 'seconds':time.time()-started,
                            'stdout':result.stdout.decode(errors='replace'),
                            'stderr':result.stderr.decode(errors='replace')})
        (out/'commands.json').write_text(json.dumps(command_log,indent=2)+'\n')
        if check and result.returncode:
            raise RuntimeError(f'command failed ({result.returncode}): {command}')
        return result.stdout.decode(errors='replace')

    def adb(*command, **kw):
        return run(['adb','-s',args.serial,*command],**kw)

    manifest = json.loads((ROOT/'scripts/fixtures/audio-output/manifest.json').read_text())
    for pad in manifest['pads']:
        if sha(ROOT/pad['reference']) != pad['reference_sha256']:
            raise ValueError('reference hash changed')
    with zipfile.ZipFile(args.test_apk) as apk:
        for pad in manifest['pads']:
            if hashlib.sha256(apk.read('assets/audio-output/'+pad['file'])).hexdigest() != pad['sha256']:
                raise ValueError('test APK does not contain the pinned fixtures')
    metadata = {'command':sys.argv, 'source_commit':run(['git','rev-parse','HEAD']).strip(),
                'source_diff':run(['git','diff','HEAD']), 'status':run(['git','status','--porcelain']),
                'apk_sha256':sha(args.apk), 'test_apk_sha256':sha(args.test_apk),
                'serial':args.serial, 'os':adb('shell','getprop'),
                'emulator_version':run([str(Path(os.environ['ANDROID_HOME'])/'emulator/emulator'),'-version']),
                'verdict_version':VERSION, 'block_playback':args.block_playback,
                'fixture_manifest':manifest}
    (out/'metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
    adb('install','-r','-t',str(args.apk),timeout=120)
    adb('install','-r','-t',str(args.test_apk),timeout=120)
    session = uuid.uuid4().hex
    exchange = f'files/audio-output/{session}'
    instrument_command = ['adb','-s',args.serial,'shell','am','instrument','-w','-r','-e','class',CLASS,
                          '-e','audioOutputSession',session,'-e','audioBlockPlayback',str(args.block_playback).lower(),
                          PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner']
    cases = [c for c in CASES if not args.case or c[0] in args.case]
    recordings = []
    instrument = recorder = None
    instrument_exit = None
    failure = None
    try:
        with (out/'instrumentation.txt').open('w') as log:
            instrument = subprocess.Popen(instrument_command,stdout=log,stderr=subprocess.STDOUT)
            command_log.append({'command':instrument_command})
            def wait_file(name, seconds):
                deadline = time.monotonic()+seconds
                while time.monotonic() < deadline:
                    if instrument.poll() is not None:
                        raise RuntimeError('screen instrumentation exited before completing its commands')
                    text = adb('exec-out','run-as',PACKAGE,'cat',exchange+'/'+name,check=False)
                    if text.strip(): return text
                    time.sleep(.05)
                raise TimeoutError(f'screen did not publish {name} within {seconds}s')
            wait_file('ready',60)
            for number,(name,action,pads,reject) in enumerate(cases):
                time.sleep(2.)  # drain the previous voice and warm the silent real output stream
                wav = out/(name+'.wav')
                capture_command = [sys.executable,str(args.devices_tool),'record',args.serial,str(wav),'4']
                with (out/(name+'-capture.txt')).open('w') as capture_log:
                    started = time.monotonic()
                    recorder = subprocess.Popen(capture_command,stdout=capture_log,stderr=subprocess.STDOUT)
                    # Discard .2s of old buffers, retain at least .5s before input.
                    time.sleep(1.05)
                    adb('shell','run-as',PACKAGE,'sh','-c',
                        f'"cat > {exchange}/command-{number}"',input=(action+'\n').encode())
                    event = json.loads(wait_file(f'event-{number}.json',4))
                    capture_exit = recorder.wait(timeout=max(.1,10-(time.monotonic()-started)))
                    recorder = None
                raw = (out/(name+'-capture.txt')).read_text()
                command_log.append({'command':capture_command,'exit':capture_exit,
                                    'seconds':time.monotonic()-started})
                capture = json.loads(raw.strip().splitlines()[-1]) if raw.strip().startswith('{') else {}
                record = {'name':name,'action':action,'pads':pads,'reject':reject,'event':event,
                          'capture':capture,'capture_exit':capture_exit}
                recordings.append(record)
                (out/'recordings.json').write_text(json.dumps(recordings,indent=2)+'\n')
                if capture_exit or not capture.get('packets'):
                    record['environment_error'] = 'recording path unconfirmed; no usable packet stream'
                    break
            adb('shell','run-as',PACKAGE,'sh','-c',
                f'"cat > {exchange}/command-{len(recordings)}"',input=b'finish\n',check=False)
            instrument_exit = instrument.wait(timeout=20)
    except (RuntimeError,ValueError,TimeoutError,subprocess.TimeoutExpired) as error:
        failure = str(error)
    finally:
        if recorder is not None and recorder.poll() is None:
            recorder.terminate()
            try: recorder.wait(timeout=10)
            except subprocess.TimeoutExpired: recorder.kill(); recorder.wait()
        if instrument is not None and instrument.poll() is None:
            adb('shell','am','force-stop',PACKAGE,check=False)
            instrument.terminate()
            try: instrument.wait(timeout=5)
            except subprocess.TimeoutExpired: instrument.kill(); instrument.wait()
        (out/'commands.json').write_text(json.dumps(command_log,indent=2)+'\n')
    results = []
    for record in recordings:
        result = {'name':record['name'], 'status':'environment_unconfirmed', **record}
        capture = record['capture']
        if record.get('environment_error'):
            results.append(result);continue
        try:
            epoch = capture['first_sample_epoch_us']/1e6
            pressed = record['event']['pressed_epoch_ms']/1000 - epoch
            values = read_wav(out/(record['name']+'.wav'))
            refs = [read_wav(ROOT/manifest['pads'][p]['reference']) for p in record['pads']]
            # Real simultaneous input reaches the pads sequentially within one multi-touch
            # gesture. Individual shifts are measured below rather than assuming exact mixing.
            if len(refs) == 1:
                verdict = compare(values,refs[0],RATE,pressed)
            else:
                combined = [[sum(samples) for samples in zip(*(r[ch] for r in refs))] for ch in range(2)]
                verdict = compare(values,combined,RATE,pressed)
            if verdict.get('error'):
                result['environment_error'] = verdict['error'] + f'; input at {pressed:.3f}s'
            else:
                quiet = is_silent(values,RATE,pressed,pressed+1.5)
                passed = (not verdict['passed'] and quiet['passed']) if record['reject'] and record['action'] != '1' else (
                    not verdict['passed'] if record['reject'] else verdict['passed'])
                result['quiet_control'] = quiet
                if record['action'] == '9':
                    released = record['event']['released_epoch_ms']/1000-epoch
                    tail = is_silent(values,RATE,released+.2,released+.7)
                    result['stop_tail'] = tail
                    passed = passed and tail['passed']
                result.update({'verdict':verdict,'status':'passed' if passed else 'failed'})
        except (KeyError,ValueError,OSError) as error:
            result['environment_error'] = str(error)
        results.append(result)
    instrument_text = (out/'instrumentation.txt').read_text() if (out/'instrumentation.txt').exists() else ''
    if failure or instrument_exit != 0 or 'OK (1 test)' not in instrument_text or len(results) != len(cases):
        results.append({'name':'screenAndRecorderComplete','status':'environment_unconfirmed',
                        'error':failure or 'screen run did not finish all requested cases'})
    exit_code = 3 if any(r['status']=='environment_unconfirmed' for r in results) else (
        2 if any(r['status']=='failed' for r in results) else 0)
    report = {'version':VERSION,'exit_code':exit_code,'results':results}
    (out/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    suite = ET.Element('testsuite',name='EmulatorOutput',tests=str(len(results)),
                       failures=str(sum(r['status']=='failed' for r in results)),
                       errors=str(sum(r['status']=='environment_unconfirmed' for r in results)))
    for result in results:
        case = ET.SubElement(suite,'testcase',classname='com.kimjisub.launchpad.audio.EmulatorOutput',name=result['name'])
        if result['status'] != 'passed':
            ET.SubElement(case,'error' if result['status']=='environment_unconfirmed' else 'failure').text = json.dumps(result)
        ET.SubElement(case,'system-out').text = json.dumps(result)
        print(result['name']+': '+result['status'])
    ET.ElementTree(suite).write(out/'TEST-EmulatorOutput.xml',encoding='utf-8',xml_declaration=True)
    print('exit',exit_code)
    return exit_code


if __name__ == '__main__':
    raise SystemExit(main())
