"""A delayed screen response must never contaminate a later verdict."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import wave
import zipfile
from unittest.mock import patch

import test_audio_output_robustness as robustness
import test_audio_output_capture as capture_tests

runner = robustness.runner


class SequenceSafetyTest(unittest.TestCase):
    def run_fault(self, fault):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            out = root/'round'
            apk, test_apk = root/'app.apk', root/'test.apk'
            apk.write_bytes(b'app')
            assets = runner.ROOT/'app/src/androidTest/assets/audio-output'
            with zipfile.ZipFile(test_apk, 'w') as archive:
                for path in assets.iterdir():
                    archive.write(path, 'assets/audio-output/'+path.name)
            now = [0.]
            commands = []
            pending_at = [None]
            class Clock(capture_tests.FakeClockSession):
                def __enter__(self):
                    if fault == 'clock_start':
                        raise RuntimeError('clock readiness timeout')
                    return self
                def measure(self, count=3):
                    if fault == 'clock_measure':
                        raise RuntimeError('invalid clock response')
                    return super().measure(count)
            clock = Clock()
            names = ['wavIsHeardAtTheEmulatorOutput', 'mp3IsHeardAtTheEmulatorOutput', 'unpressedPadIsRejected']
            def sleep(seconds):
                now[0] += seconds
            def run(command, **kwargs):
                value = b''
                if 'date' in command:
                    if fault == 'persistent_clock':
                        raise RuntimeError('one-shot clock startup must not be measured')
                    value = b'3000000000000000000'
                if command[-1].endswith('/ready') and fault != 'ready':
                    value = b'ready'
                if 'cat >' in command[-1] and kwargs.get('input') != b'finish\n':
                    number = len(commands)
                    commands.append(number)
                    if fault == 'send' and number == 1:
                        return subprocess.CompletedProcess(command, 1, b'', b'transport disconnected')
                    if number == 1:
                        pending_at[0] = now[0]+4.2
                if '/event-' in command[-1]:
                    number = int(command[-1].split('event-')[1].split('.')[0])
                    # A real screen processes commands in order. Input 1 is still
                    # pending when its four-second response wait expires.
                    if number != 1 or now[0] >= pending_at[0]:
                        value = json.dumps(dict(pressed_epoch_ms=3000, pressed_end_epoch_ms=3001,
                                                released_epoch_ms=3040, released_end_epoch_ms=3041,
                                                finished_epoch_ms=3042)).encode()
                return subprocess.CompletedProcess(command, 0, value, b'')
            class Process:
                done = False
                def poll(self): return 0 if self.done else None
                def terminate(self): self.done = True
                def kill(self): self.done = True
                def wait(self, timeout=None): self.done = True; return 0
            def popen(command, **kwargs):
                log = kwargs['stdout']
                if 'record' in command:
                    log.write(json.dumps({'packets':800, 'sample_rate':48000, 'gaps':[]})+'\n')
                    robustness.RobustCaptureTest.write_silence(out/'stream.wav')
                    (out/'stream.packets.json').write_text(json.dumps(capture_tests.PacketTimelineTest().timeline()))
                else:
                    log.write('OK (1 test)\n')
                log.flush()
                return Process()
            def extract(source, target, timeline, event):
                # Model the late previous tone entering the next silence control.
                # Analysis itself uses the real WAV reader and silence verdict.
                if target.stem in (names[0], names[2]):
                    reference = runner.ROOT/'scripts/fixtures/audio-output/pad-0.wav'
                    with wave.open(str(reference), 'rb') as wav:
                        tone = wav.readframes(48000)
                    with wave.open(str(target), 'wb') as wav:
                        wav.setnchannels(2); wav.setsampwidth(2); wav.setframerate(48000)
                        wav.writeframes(bytes(48000*4)+tone+bytes(96000*4))
                else:
                    robustness.RobustCaptureTest.write_silence(target)
                return dict(pressed_s=.75, released_s=.9, input_uncertainty_s=.1, sample_rate=48000)
            argv = ['run_audio_output.py', '--serial','test-device', '--devices-tool',str(root/'devices.py'),
                    '--output',str(out), '--apk',str(apk), '--test-apk',str(test_apk)]
            for name in names:
                argv += ['--case',name]
            with patch.object(sys,'argv',argv), patch.dict(os.environ,{'ANDROID_HOME':str(root)}), \
                 patch.object(runner.time,'monotonic',side_effect=lambda:now[0]), \
                 patch.object(runner.time,'sleep',side_effect=sleep), \
                 patch.object(runner.subprocess,'run',side_effect=run), \
                 patch.object(runner.subprocess,'Popen',side_effect=popen), \
                 patch.object(runner,'extract_case',side_effect=extract), \
                 patch.object(runner,'create_clock_session',return_value=clock,create=True):
                code = runner.main()
            if fault in ('persistent_clock','clock_start','clock_measure'):
                self.assertTrue(clock.closed, 'clock connection must close after collection stops')
            return code, json.loads((out/'results.json').read_text()), commands, \
                json.loads((out/'commands.json').read_text()), (out/'TEST-EmulatorOutput.xml').read_text()

    def test_late_input_stops_collection_without_failing_the_next_case(self):
        code, report, commands, traces, xml = self.run_fault('late')
        results = {r['name']:r for r in report['results']}
        self.assertEqual('environment_unconfirmed', results['unpressedPadIsRejected']['status'], report)
        self.assertEqual(3, code)
        self.assertEqual([0,1], commands)
        self.assertEqual('passed',results['wavIsHeardAtTheEmulatorOutput']['status'])
        self.assertIn('TimeoutError',results['mp3IsHeardAtTheEmulatorOutput']['environment_error'])
        self.assertIn('unpressedPadIsRejected',xml)
        recorder = next(t for t in traces if 'record' in t['command'])
        self.assertEqual(0,recorder['exit'])

    def test_persistent_clock_is_used_and_closed_after_input_timeout(self):
        code, report, commands, traces, xml = self.run_fault('persistent_clock')
        results = {r['name']:r for r in report['results']}
        self.assertEqual('passed',results['wavIsHeardAtTheEmulatorOutput']['status'],report)
        self.assertEqual('environment_unconfirmed',results['unpressedPadIsRejected']['status'])
        self.assertEqual(3,code)
        self.assertEqual([0,1],commands)
        self.assertFalse(any('date' in trace['command'] for trace in traces))
        measured = [trace for trace in traces if trace.get('kind')=='clock_measurement']
        self.assertEqual(3,len(measured))
        self.assertTrue(all(len(trace['samples'])==3 for trace in measured))

    def test_clock_startup_failure_is_unconfirmed_and_closes_the_connection(self):
        code, report, commands, traces, xml = self.run_fault('clock_start')
        self.assertEqual(3,code)
        self.assertEqual([],commands)
        self.assertTrue(all(r['status']=='environment_unconfirmed' for r in report['results']))
        self.assertIn('clock readiness timeout',report['results'][0]['environment_error'])

    def test_clock_response_failure_is_preserved_without_zero_offset_fallback(self):
        code, report, commands, traces, xml = self.run_fault('clock_measure')
        self.assertEqual(3,code)
        self.assertTrue(all(r['status']=='environment_unconfirmed' for r in report['results']))
        self.assertIn('invalid clock response',report['results'][0]['environment_error'])
        self.assertFalse(any('date' in trace['command'] for trace in traces))
        self.assertTrue(any(trace.get('kind')=='clock_measurement' and
                            'invalid clock response' in trace.get('error','') for trace in traces))

    def test_ready_timeout_reports_every_requested_case(self):
        code, report, commands, traces, xml = self.run_fault('ready')
        self.assertEqual(3,code)
        self.assertEqual([],commands)
        for name in ['wavIsHeardAtTheEmulatorOutput','mp3IsHeardAtTheEmulatorOutput','unpressedPadIsRejected']:
            case = next(r for r in report['results'] if r['name']==name)
            self.assertEqual('environment_unconfirmed',case['status'])
            self.assertIn('TimeoutError',case['environment_error'])
            self.assertIn(name,xml)

    def test_failed_command_preserves_cause_and_remaining_names(self):
        code, report, commands, traces, xml = self.run_fault('send')
        results = {r['name']:r for r in report['results']}
        self.assertEqual(3,code)
        self.assertEqual('passed',results['wavIsHeardAtTheEmulatorOutput']['status'])
        self.assertIn('command failed',results['mp3IsHeardAtTheEmulatorOutput']['environment_error'])
        self.assertIn('RuntimeError',results['mp3IsHeardAtTheEmulatorOutput']['environment_error'])
        self.assertEqual('environment_unconfirmed',results['unpressedPadIsRejected']['status'])


if __name__=='__main__': unittest.main()
