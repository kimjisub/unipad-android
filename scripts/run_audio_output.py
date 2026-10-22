#!/usr/bin/env python3
"""Drive real pads while devices.py records the lent emulator's external output.

The caller rents an audio-enabled emulator and returns it. This command never
starts devices, reads authentication files or plays a sound from the recorder.
Each invocation creates one immutable evidence round; archive it before a rerun.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid
import wave
import xml.etree.ElementTree as ET
import zipfile

from audio_output import RATE, VERSION, OUTPUT_WINDOW_S, compare, is_silent, levels, read_wav

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.kimjisub.launchpad.dev'
CLASS = 'com.kimjisub.launchpad.audio.EmulatorOutputScreenTest#drivesScreenForExternalOutput'
# Input, references and control rules travel together; names are stable report IDs.
def case(name, pads, references, rule='match', *, alternate_pad=None,
         hold_ms=40, stop_window_s=None):
    return dict(name=name, input_pads=pads, reference_pads=references, rule=rule,
                alternate_pad=alternate_pad, hold_ms=hold_ms, stop_window_s=stop_window_s)


CASES = [
    case('wavIsHeardAtTheEmulatorOutput',[0],[0]),
    case('mp3IsHeardAtTheEmulatorOutput',[1],[1]),
    case('oggIsHeardAtTheEmulatorOutput',[2],[2]),
    case('m4aIsHeardAtTheEmulatorOutput',[3],[3]),
    case('bareMp3NamedWavIsHeardAtTheEmulatorOutput',[4],[4]),
    case('taggedMp3NamedWavIsHeardAtTheEmulatorOutput',[5],[5]),
    case('leftChannelIsHeardSeparately',[6],[6]),
    case('rightChannelIsHeardSeparately',[7],[7]),
    case('unpressedPadIsRejected',[],[0],'silence'),
    case('emptyPadIsRejected',[63],[0],'silence'),
    case('silentFileIsRejected',[8],[0],'silence'),
    case('differentPadIsRejected',[1],[0],'alternate',alternate_pad=1),
    case('heldLoopStopsAfterRelease',[9],[9],hold_ms=1250,stop_window_s=[.6,1.1]),
    case('twoPadsAreHeardTogether',[0,1],[0,1]),
]
CASE_BY_NAME = {c['name']:c for c in CASES}
# Observed stream timestamp jitter is up to 36 ms in either direction.
PACKET_UNCERTAINTY_S = .036



def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_timeline(log: dict) -> None:
    """Require the managed recorder's complete sample-position audit trail."""
    if log.get('sample_rate') != 48000 or log.get('channels') != 2:
        raise ValueError('missing or unsupported packet timeline format')
    packets, gaps = log.get('packets'), log.get('gaps')
    if not isinstance(packets,list) or not packets or not isinstance(gaps,list):
        raise ValueError('missing packet or gap records')
    previous_time, end = 0, 0
    gap_by_end = {}
    for gap in gaps:
        at, frames = gap.get('at_frame'), gap.get('frames')
        if not isinstance(at,int) or not isinstance(frames,int) or at < 0 or frames <= 0:
            raise ValueError('invalid inserted-gap position')
        gap_by_end[at+frames] = (at,frames)
    used = set()
    for packet in packets:
        timestamp, at, frames = (packet.get(k) for k in ('timestamp_us','at_frame','frames'))
        if (not all(isinstance(v,int) for v in (timestamp,at,frames)) or
                timestamp <= previous_time or at < end or frames <= 0):
            raise ValueError('invalid packet timestamp, frame count or position')
        if at != end:
            if gap_by_end.get(at) != (end,at-end):
                raise ValueError('packet positions contain an unexplained missing interval')
            used.add(at)
        previous_time, end = timestamp, at+frames
    if len(used) != len(gaps):
        raise ValueError('gap records disagree with written packet positions')


def event_frame(log: dict, epoch_ms: int) -> float:
    """Map against the closest packet, never the stream's first timestamp."""
    validate_timeline(log)
    epoch_us = epoch_ms*1000
    packet = min(log['packets'],key=lambda p:abs(p['timestamp_us']-epoch_us))
    offset = (epoch_us-packet['timestamp_us'])*log['sample_rate']/1_000_000
    if abs(epoch_us-packet['timestamp_us']) > 100_000:
        raise ValueError('screen input is outside confirmed nearby packet timestamps')
    return packet['at_frame']+offset



def clock_bounds(event: dict) -> dict:
    """Bound guest-minus-host offset using round trips bracketing this input.

    Intersect all measurements: a clock step/drift that cannot share an offset,
    host wall-clock jump, or more than 50 ms unresolved timing is environmental.
    Guest millisecond events/date output receive a 1 ms quantization allowance.
    """
    clock = event.get('clock')
    if not isinstance(clock,dict) or not clock.get('before') or not clock.get('after'):
        raise ValueError('missing per-input clock measurements')
    lower, upper = -math.inf, math.inf
    samples = clock['before']+clock['after']
    for sample in samples:
        before,after,guest,elapsed = (sample.get(k) for k in
            ('host_before_us','host_after_us','guest_us','monotonic_elapsed_us'))
        if (not all(isinstance(v,int) and v > 0 for v in (before,after,guest,elapsed)) or
                after < before or abs(after-before-elapsed) > 5000):
            raise ValueError('invalid measurement or host wall clock changed during sampling')
        lower=max(lower,guest-after-1000)
        upper=min(upper,guest-before+1000)
    if upper < lower or upper-lower > 50_000:
        raise ValueError('guest clock changed or clock uncertainty exceeds 50 ms')
    pressed,pressed_end,released,released_end,finished=(event.get(k) for k in
        ('pressed_epoch_ms','pressed_end_epoch_ms','released_epoch_ms','released_end_epoch_ms','finished_epoch_ms'))
    if (not all(isinstance(v,(int,float)) and math.isfinite(v) for v in (pressed,pressed_end,released,released_end,finished)) or
            not clock['before'][-1]['guest_us'] <= pressed*1000 <= pressed_end*1000 <= released*1000 <=
            released_end*1000 <= finished*1000 <= clock['after'][0]['guest_us']):
        raise ValueError('clock measurements do not bracket the input')
    if clock['before'][-1]['host_after_us'] > clock['after'][0]['host_before_us']:
        raise ValueError('clock measurements are out of order')
    return {'offset_lower_us':lower,'offset_upper_us':upper,
            'offset_midpoint_us':(lower+upper)/2,'uncertainty_us':upper-lower}

def overlapping_gaps(log: dict, start: int, end: int) -> list[dict]:
    return [gap for gap in log['gaps']
            if gap['at_frame'] < end and gap['at_frame']+gap['frames'] > start]


def extract_case(source: Path, target: Path, log: dict, event: dict) -> dict:
    """Copy original PCM, retaining packet-mapped input and environmental errors."""
    clock = clock_bounds(event)
    # Baseline ends at the earliest possible press; stop checks use latest release.
    pressed = event_frame(log,event['pressed_epoch_ms']-clock['offset_upper_us']/1000) - PACKET_UNCERTAINTY_S*log['sample_rate']
    latest_press = event_frame(log,event['pressed_end_epoch_ms']-clock['offset_lower_us']/1000) + PACKET_UNCERTAINTY_S*log['sample_rate']
    released = event_frame(log,event['released_end_epoch_ms']-clock['offset_lower_us']/1000) + PACKET_UNCERTAINTY_S*log['sample_rate']
    with wave.open(str(source),'rb') as reader:
        rate = reader.getframerate()
        if rate != log['sample_rate'] or reader.getnchannels() != 2 or reader.getsampwidth() != 2:
            raise ValueError('recording and packet timeline formats disagree')
        last = log['packets'][-1]
        if last['at_frame']+last['frames'] != reader.getnframes():
            raise ValueError('packet timeline does not cover the entire recording')
        start, frames = round(pressed-.75*rate), 4*rate
        if released < pressed or start < round(.2*rate) or start+frames > reader.getnframes():
            raise ValueError('four-second case baseline/postroll is outside recorded PCM')
        gaps = overlapping_gaps(log,start,start+frames)
        reader.setpos(start)
        pcm = reader.readframes(frames)
        with wave.open(str(target),'wb') as writer:
            writer.setparams(reader.getparams())
            writer.writeframes(pcm)
    result = {'file':str(target),'seconds':4.,'source_frame_offset':start,'sample_rate':rate,
              'pressed_s':(pressed-start)/rate,'released_s':(released-start)/rate,
              'gaps':gaps,'clock':clock,'packet_uncertainty_s':PACKET_UNCERTAINTY_S,'input_uncertainty_s':(latest_press-pressed)/rate}
    if gaps:
        result['environment_error'] = 'managed recorder inserted silence inside this case window'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--devices-tool', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--test-apk', required=True, type=Path)
    parser.add_argument('--block-playback', action='store_true')
    parser.add_argument('--case', action='append', choices=[c['name'] for c in CASES])
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()):
        parser.error('output folder must be empty; preserve and attach the previous round first')
    cases = [c for c in CASES if not args.case or c['name'] in args.case]
    try:
        results = execute_round(args,out,cases)
    except Exception as error:
        results = [{'name':c['name'],'status':'environment_unconfirmed',
                    'environment_error':f'{type(error).__name__}: {error}'} for c in cases]
    return write_report(out,results)


def validate_fixtures(manifest, test_apk):
    for pad in manifest['pads']:
        if sha(ROOT/pad['reference']) != pad['reference_sha256']:
            raise ValueError('reference hash changed')
    with zipfile.ZipFile(test_apk) as apk:
        for name,digest in manifest['pack_files'].items():
            if hashlib.sha256(apk.read('assets/audio-output/'+name)).hexdigest() != digest:
                raise ValueError(f'test APK pack mapping changed: {name}')
        for pad in manifest['pads']:
            if hashlib.sha256(apk.read('assets/audio-output/'+pad['file'])).hexdigest() != pad['sha256']:
                raise ValueError('test APK does not contain the pinned fixtures')


def judge_record(record, manifest, out):
    result = {'name':record['name'], 'status':'environment_unconfirmed', **record}
    capture = record.get('capture',{})
    if record.get('environment_error') or not capture:
        result.setdefault('environment_error','capture did not complete')
        return result
    try:
        pressed = capture['pressed_s']
        uncertainty = capture['input_uncertainty_s']
        case_path = out/(record['name']+'.wav')
        values = read_wav(case_path)
        result['raw_channels'] = [levels(c) for c in read_wav(case_path,capture['sample_rate'])]
        rule = record.get('case') or CASE_BY_NAME[record['name']]
        refs = [read_wav(ROOT/manifest['pads'][p]['reference']) for p in rule['reference_pads']]
        # Compare the time/frequency pattern of both notes, irrespective of sample phase.
        if len(refs) == 1:
            verdict = compare(values,refs[0],RATE,pressed,uncertainty)
        else:
            combined = [[sum(samples) for samples in zip(*(r[ch] for r in refs))] for ch in range(2)]
            verdict = compare(values,combined,RATE,pressed,uncertainty)
        if verdict.get('error'):
            result['environment_error'] = verdict['error'] + f'; input at {pressed:.3f}s'
        else:
            quiet = is_silent(values,RATE,pressed,pressed+OUTPUT_WINDOW_S+uncertainty)
            passed = verdict['passed']
            if rule['rule'] == 'silence':
                passed = not verdict['passed'] and quiet['passed']
            elif rule['rule'] == 'alternate':
                actual = read_wav(ROOT/manifest['pads'][rule['alternate_pad']]['reference'])
                result['actual_pad_verdict'] = compare(values,actual,RATE,pressed,uncertainty)
                passed = not verdict['passed'] and result['actual_pad_verdict']['passed']
            result['quiet_control'] = quiet
            if rule['stop_window_s'] is not None:
                released = capture['released_s']
                start,end = [released+offset for offset in rule['stop_window_s']]
                if any(len(channel) < math.ceil(end*RATE) for channel in values):
                    raise ValueError('loop stop confirmation extends beyond recorded PCM')
                tail = is_silent(values,RATE,start,end)
                active = [t/100 for t in range(int(released*100),len(values[0])*100//RATE-1)
                          if not is_silent(values,RATE,t/100,(t+1)/100)['passed']]
                result['observed_stop_tail_s'] = max(0.,active[-1]+.01-released) if active else 0.
                result['stop_tail'] = tail
                passed = passed and tail['passed']
            result.update({'verdict':verdict,'status':'passed' if passed else 'failed'})
    except Exception as error:
        result['environment_error'] = f'{type(error).__name__}: {error}'
    return result


def judge_records(records, manifest, out):
    return [judge_record(record,manifest,out) for record in records]


def create_clock_session(devices_tool, serial):
    """Use the managed clock transport shipped alongside the recorder."""
    path = devices_tool.resolve().with_name('android_clock.py')
    spec = importlib.util.spec_from_file_location('audio_capture_clock',path)
    if spec is None or spec.loader is None:
        raise ValueError('managed clock tool cannot be loaded')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    session = module.ClockSession(serial)
    session.source_sha256 = sha(path)
    return session


def execute_round(args, out, cases):
    command_log = []

    def run(command, *, timeout=30, check=True, input=None):
        started_us = time.time_ns()//1000
        started_mono = time.monotonic_ns()//1000
        trace = {'command':list(map(str,command)), 'epoch_s':started_us/1_000_000,
                 'host_before_us':started_us}
        try:
            result = subprocess.run(trace['command'], input=input, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE, timeout=timeout)
            trace.update(exit=result.returncode,stdout=result.stdout.decode(errors='replace'),
                         stderr=result.stderr.decode(errors='replace'))
        except Exception as error:
            trace.update(exit=None,error=f'{type(error).__name__}: {error}',
                         stdout=(getattr(error,'stdout',None) or b'').decode(errors='replace'),
                         stderr=(getattr(error,'stderr',None) or b'').decode(errors='replace'))
            raise
        finally:
            elapsed = time.monotonic_ns()//1000-started_mono
            trace.update(host_after_us=time.time_ns()//1000,monotonic_elapsed_us=elapsed,
                         seconds=elapsed/1_000_000)
            command_log.append(trace)
            (out/'commands.json').write_text(json.dumps(command_log,indent=2)+'\n')
        if check and result.returncode:
            raise RuntimeError(f'command failed ({result.returncode}): {command}')
        return result.stdout.decode(errors='replace')

    def adb(*command, **kw):
        return run(['adb','-s',args.serial,*command],**kw)

    def measure_clock():
        trace = {'kind':'clock_measurement', 'command':['clock.measure', '3']}
        try:
            batch = clock.measure(3)
            trace.update(batch)
            return batch['samples']
        except Exception as error:
            trace['error'] = f'{type(error).__name__}: {error}'
            raise
        finally:
            command_log.append(trace)
            (out/'commands.json').write_text(json.dumps(command_log,indent=2)+'\n')

    manifest = json.loads((ROOT/'scripts/fixtures/audio-output/manifest.json').read_text())
    validate_fixtures(manifest,args.test_apk)
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
    recordings = []
    instrument = recorder = clock = None
    clock_trace = instrument_trace = None
    instrument_exit = None
    capture_exit = None
    failure = None
    try:
        with (out/'instrumentation.txt').open('w') as log:
            instrument = subprocess.Popen(instrument_command,stdout=log,stderr=subprocess.STDOUT)
            instrument_trace = {'command':instrument_command}
            command_log.append(instrument_trace)
            def wait_file(name, seconds):
                deadline = time.monotonic()+seconds
                while time.monotonic() < deadline:
                    if instrument.poll() is not None:
                        raise RuntimeError('screen instrumentation exited before completing its commands')
                    text = adb('exec-out','run-as',PACKAGE,'cat',exchange+'/'+name,check=False)
                    if text.strip() == "ready" or text.lstrip().startswith("{"): return text
                    time.sleep(.05)
                raise TimeoutError(f'screen did not publish {name} within {seconds}s')
            wait_file('ready',60)
            clock = create_clock_session(args.devices_tool,args.serial)
            clock_trace = {'kind':'clock_session', 'command':['adb','-s',args.serial,'shell','-T'],
                           'tool_sha256':clock.source_sha256}
            command_log.append(clock_trace)
            try:
                clock.__enter__()
                clock_trace['startup_elapsed_us'] = clock.startup_elapsed_us
            except Exception as error:
                clock_trace['error'] = f'{type(error).__name__}: {error}'
                raise
            time.sleep(2.)  # warm the open, silent Oboe stream once
            capture_command = [sys.executable,str(args.devices_tool),'record',args.serial,str(out/'stream.wav'),'120']
            capture_trace = {'command':capture_command}
            command_log.append(capture_trace)
            with (out/'stream-capture.txt').open('w') as capture_log:
                recorder = subprocess.Popen(capture_command,stdout=capture_log,stderr=subprocess.STDOUT)
                # The managed recorder stops within ten seconds when no packets arrive.
                # Keep one connection for the round; per-case timestamps, not startup
                # sleeps, decide whether baseline/postroll actually exist.
                time.sleep(9.)
                if recorder.poll() is not None:
                    raise RuntimeError('recorder ended during initial packet preparation')
                for number,rule in enumerate(cases):
                    record = {'name':rule['name'],'case':rule}
                    recordings.append(record)
                    try: clock_before=measure_clock()
                    except Exception as error:
                        clock_before=[]
                        record['environment_error']=f'clock before input: {type(error).__name__}: {error}'
                    try:
                        adb('shell','run-as',PACKAGE,'sh','-c',
                            f'"cat > {exchange}/command.tmp && mv {exchange}/command.tmp {exchange}/command-{number}"',
                            input=(json.dumps({'pads':rule['input_pads'],'hold_ms':rule['hold_ms']})+'\n').encode())
                        event_text = wait_file(f'event-{number}.json',4)
                    except Exception as error:
                        # A pending input can play during the next baseline. Stop
                        # sending commands until a new round starts a fresh screen.
                        record['environment_error'] = f'input collection: {type(error).__name__}: {error}'
                        raise
                    try:
                        event = json.loads(event_text)
                        record['event'] = event
                        event['clock']={'before':clock_before,'after':measure_clock()}
                    except Exception as error:
                        record['environment_error']=f'input event or clock: {type(error).__name__}: {error}'
                    (out/'recordings.json').write_text(json.dumps(recordings,indent=2)+'\n')
                    if instrument.poll() is not None:
                        raise RuntimeError('screen instrumentation ended during case collection')
                    time.sleep(2.5)  # previous one-second voice ends before the next baseline
                time.sleep(1.)  # retain the last case's complete postroll
                recorder.terminate()  # managed recorder flushes its WAV on SIGTERM
                capture_exit = recorder.wait(timeout=10)
                recorder = None
                capture_trace['exit'] = capture_exit
            adb('shell','run-as',PACKAGE,'sh','-c',
                f'"cat > {exchange}/command.tmp && mv {exchange}/command.tmp {exchange}/command-{len(recordings)}"',input=b'finish\n',check=False)
            instrument_exit = instrument.wait(timeout=20)
    except Exception as error:
        failure = f'{type(error).__name__}: {error}'
    finally:
        if clock is not None:
            try:
                clock.close()
                if clock_trace is not None: clock_trace['exit'] = 0
            except Exception as error:
                if clock_trace is not None: clock_trace['error'] = f'{type(error).__name__}: {error}'
                failure = failure or f'clock cleanup: {type(error).__name__}: {error}'
        for process in (recorder,instrument):
            if process is None:
                continue
            try:
                code = process.poll()
                if code is None:
                    if process is instrument:
                        try: adb('shell','am','force-stop',PACKAGE,check=False)
                        except Exception as error: failure = failure or f'cleanup: {type(error).__name__}: {error}'
                    process.terminate()
                    try:
                        code=process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        code=process.wait(timeout=10)
                if process is instrument:
                    instrument_trace['exit']=code
                if process is recorder:
                    capture_exit=code
                    capture_trace['exit']=code
            except Exception as error:
                failure = failure or f'cleanup: {type(error).__name__}: {error}'
        (out/'commands.json').write_text(json.dumps(command_log,indent=2)+'\n')
    # Finalize and analyze all completed inputs even if a later input failed.
    try:
        capture = json.loads((out/'stream-capture.txt').read_text().strip().splitlines()[-1])
        timeline_path = out/'stream.packets.json'
        timeline = json.loads(timeline_path.read_text()) if timeline_path.exists() else {}
        for record in recordings:
            record['capture_exit'] = capture_exit
            record['capture'] = dict(capture)
            record['capture']['stream_levels'] = record['capture'].pop('levels',[])
            record['capture']['stream_gap_seconds'] = record['capture'].pop('gap_seconds',0.)
            if record.get('environment_error'):
                continue
            if capture_exit != 0 or not capture.get('packets'):
                record['environment_error'] = 'recording path unconfirmed; no usable packet stream'
                continue
            try:
                record['capture'].update(extract_case(
                    out/'stream.wav', out/(record['name']+'.wav'),
                    timeline,record['event']))
                if record['capture'].get('environment_error'):
                    record['environment_error'] = record['capture']['environment_error']
            except Exception as error:
                record['environment_error'] = f'{type(error).__name__}: {error}'
        (out/'recordings.json').write_text(json.dumps(recordings,indent=2)+'\n')
    except Exception as error:
        failure = failure or f'capture finalization: {type(error).__name__}: {error}'
    results = judge_records(recordings,manifest,out)
    collected = {record['name'] for record in recordings}
    for rule in cases:
        if rule['name'] not in collected:
            results.append({'name':rule['name'], 'status':'environment_unconfirmed',
                            'environment_error':'case was not collected: '+(failure or 'screen run stopped')})
    instrument_text = (out/'instrumentation.txt').read_text() if (out/'instrumentation.txt').exists() else ''
    if failure or instrument_exit != 0 or 'OK (1 test)' not in instrument_text or len(results) != len(cases):
        results.append({'name':'screenAndRecorderComplete','status':'environment_unconfirmed',
                        'error':failure or 'screen run did not finish all requested cases'})
    return results


def write_report(out, results):
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
