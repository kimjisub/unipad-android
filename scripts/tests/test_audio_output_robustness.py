"""Timing and environmental faults must not masquerade as app failures."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import wave
from unittest.mock import patch

import test_audio_output_capture as capture_tests

runner = capture_tests.runner


class RobustCaptureTest(unittest.TestCase):
    def test_injection_duration_and_packet_jitter_bound_the_input(self):
        with tempfile.TemporaryDirectory() as temp:
            source, target = Path(temp)/'source.wav', Path(temp)/'case.wav'
            with wave.open(str(source), 'wb') as wav:
                wav.setnchannels(2); wav.setsampwidth(2); wav.setframerate(48000)
                wav.writeframes(bytes(384000*4))
            event = {'pressed_epoch_ms':3000, 'pressed_end_epoch_ms':3200,
                     'released_epoch_ms':3240, 'released_end_epoch_ms':3440,
                     'finished_epoch_ms':3441, 'clock':capture_tests.ClockAndControlTest.clock(offset=0,rtt=2)}
            capture = runner.extract_case(source,target,capture_tests.PacketTimelineTest().timeline(),event)
            # 200 ms injection + 2.002 ms clock bounds + 72 ms packet bounds.
            self.assertGreaterEqual(capture['input_uncertainty_s'],.274)
            self.assertAlmostEqual(capture['released_s']-capture['pressed_s'],.514002,delta=.0001)

    def test_packet_jitter_does_not_put_the_sound_in_the_baseline(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target=Path(temp)/'source.wav',Path(temp)/'case.wav'
            reference=runner.ROOT/'scripts/fixtures/audio-output/pad-0.wav'
            with wave.open(str(reference),'rb') as wav:tone=wav.readframes(48000)
            with wave.open(str(source),'wb') as wav:
                wav.setnchannels(2);wav.setsampwidth(2);wav.setframerate(48000)
                wav.writeframes(bytes(96000*4)+tone+bytes(240000*4))
            timeline=capture_tests.PacketTimelineTest().timeline()
            for packet in timeline['packets']:packet['timestamp_us']-=35000
            event={'pressed_epoch_ms':3000,'pressed_end_epoch_ms':3001,
                   'released_epoch_ms':3040,'released_end_epoch_ms':3041,
                   'finished_epoch_ms':3042,'clock':capture_tests.ClockAndControlTest.clock(offset=0,rtt=2)}
            capture=runner.extract_case(source,target,timeline,event)
            verdict=runner.compare(runner.read_wav(target),runner.read_wav(reference),runner.RATE,
                                   capture['pressed_s'],capture['input_uncertainty_s'])
            self.assertTrue(verdict['passed'],verdict)

    def test_missing_or_reversed_injection_bounds_are_unconfirmed(self):
        event = {'pressed_epoch_ms':3000, 'released_epoch_ms':3100,
                 'finished_epoch_ms':3101, 'clock':capture_tests.ClockAndControlTest.clock(offset=0,rtt=2)}
        for ends in [{}, {'pressed_end_epoch_ms':2999,'released_end_epoch_ms':3100},
                     {'pressed_end_epoch_ms':3200,'released_end_epoch_ms':3100}]:
            with self.subTest(ends=ends), self.assertRaises(ValueError):
                runner.clock_bounds(event|ends)

    def test_loop_tail_outside_pcm_is_unconfirmed(self):
        with tempfile.TemporaryDirectory() as temp:
            out=Path(temp)
            self.write_silence(out/'heldLoopStopsAfterRelease.wav')
            record=self.record('heldLoopStopsAfterRelease')
            record['capture']['released_s']=3.
            result=runner.judge_record(record, self.manifest(), out)
            self.assertEqual('environment_unconfirmed',result['status'],result)
            self.assertIn('stop',result['environment_error'])

    def test_one_broken_case_preserves_other_case_verdicts(self):
        with tempfile.TemporaryDirectory() as temp:
            out=Path(temp)
            self.write_silence(out/'unpressedPadIsRejected.wav')
            (out/'wavIsHeardAtTheEmulatorOutput.wav').write_bytes(b'corrupt WAV')
            broken=self.record('wavIsHeardAtTheEmulatorOutput')
            healthy=self.record('unpressedPadIsRejected')
            results=runner.judge_records([broken,healthy],self.manifest(),out)
            self.assertEqual(['environment_unconfirmed','passed'],[r['status'] for r in results])

    def test_controls_use_case_rules_instead_of_action_strings(self):
        with tempfile.TemporaryDirectory() as temp:
            out=Path(temp)
            self.write_silence(out/'differentPadIsRejected.wav')
            record=self.record('differentPadIsRejected')
            # A silent wrong-pad control cannot pass just because its old action changed.
            record['action']='another-input-name'
            result=runner.judge_record(record,self.manifest(),out)
            self.assertEqual('failed',result['status'],result)
            self.assertIn('actual_pad_verdict',result)

    @staticmethod
    def manifest():
        return json.loads((runner.ROOT/'scripts/fixtures/audio-output/manifest.json').read_text())

    @staticmethod
    def record(name):
        return {'name':name,'capture':{'pressed_s':.75,'released_s':.9,
                                     'input_uncertainty_s':.1,'sample_rate':48000}}

    @staticmethod
    def write_silence(path):
        with wave.open(str(path),'wb') as wav:
            wav.setnchannels(2);wav.setsampwidth(2);wav.setframerate(48000)
            wav.writeframes(bytes(192000*4))


class StartupReportTest(unittest.TestCase):
    def test_missing_test_apk_produces_environment_report_and_xml(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);out=root/'round'
            command=[sys.executable,str(runner.ROOT/'scripts/run_audio_output.py'),
                     '--serial','test-device','--devices-tool',str(root/'devices.py'),
                     '--output',str(out),'--apk',str(root/'app.apk'),'--test-apk',str(root/'test.apk')]
            result=subprocess.run(command,cwd=root,env={k:v for k,v in os.environ.items() if k!='ANDROID_HOME'},
                                  capture_output=True,text=True,timeout=10)
            self.assertEqual(3,result.returncode,result.stderr)
            self.assertEqual(3,json.loads((out/'results.json').read_text())['exit_code'])
            self.assertTrue((out/'TEST-EmulatorOutput.xml').exists())

    def test_empty_recorder_output_produces_report(self):
        self.run_fault('empty')

    def test_cleanup_timeout_produces_report(self):
        self.run_fault('cleanup')

    def test_missing_android_home_produces_report(self):
        self.run_fault('android_home')

    def test_running_outside_repository_produces_report(self):
        self.run_fault('outside_repository')

    def run_fault(self, fault):
        import zipfile
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);out=root/'round';apk=root/'app.apk';apk.write_bytes(b'app')
            test_apk=root/'test.apk'
            with zipfile.ZipFile(test_apk,'w') as archive:
                for path in (runner.ROOT/'app/src/androidTest/assets/audio-output').iterdir():
                    archive.write(path,'assets/audio-output/'+path.name)
            def run(command, **kwargs):
                value=b''
                if command[:2]==['git','rev-parse']:
                    if fault=='outside_repository':
                        return subprocess.CompletedProcess(command,128,b'',b'not a git repository')
                    value=b'base-commit'
                if 'date' in command:value=b'3000000000000000000'
                if command[-1].endswith('/ready'):value=b'ready'
                if command[-1].endswith('/event-0.json'):
                    value=json.dumps({'pressed_epoch_ms':3000,'pressed_end_epoch_ms':3001,
                                      'released_epoch_ms':3040,'released_end_epoch_ms':3041,
                                      'finished_epoch_ms':3041}).encode()
                if fault=='cleanup' and 'force-stop' in command:
                    raise subprocess.TimeoutExpired(command,30)
                return subprocess.CompletedProcess(command,0,value,b'')
            class Process:
                def poll(self):return None
                def terminate(self):pass
                def kill(self):pass
                def wait(self,timeout=None):return 0
            argv=['run_audio_output.py','--serial','test-device','--devices-tool',str(root/'devices.py'),
                  '--output',str(out),'--apk',str(apk),'--test-apk',str(test_apk),
                  '--case','wavIsHeardAtTheEmulatorOutput']
            env={k:v for k,v in os.environ.items() if k!='ANDROID_HOME'}
            if fault!='android_home':env['ANDROID_HOME']=str(root)
            with patch.object(sys,'argv',argv),patch.dict(os.environ,env,clear=True), \
                 patch.object(runner.subprocess,'run',side_effect=run), \
                 patch.object(runner.subprocess,'Popen',return_value=Process()), \
                 patch.object(runner.time,'sleep'):
                code=runner.main()
            self.assertEqual(3,code)
            self.assertEqual(3,json.loads((out/'results.json').read_text())['exit_code'])
            self.assertTrue((out/'TEST-EmulatorOutput.xml').exists())
            if fault=='cleanup':
                commands=json.loads((out/'commands.json').read_text())
                self.assertTrue(any('force-stop' in c['command'] and c.get('error')
                                    for c in commands),commands)

    def test_corrupt_event_preserves_previous_and_next_recordings(self):
        import time
        import zipfile
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);out=root/'round';apk=root/'app.apk';apk.write_bytes(b'app')
            test_apk=root/'test.apk'
            with zipfile.ZipFile(test_apk,'w') as archive:
                for path in (runner.ROOT/'app/src/androidTest/assets/audio-output').iterdir():
                    archive.write(path,'assets/audio-output/'+path.name)
            epoch_ms=int(time.time()*1000)
            timeline=capture_tests.PacketTimelineTest().timeline()
            for packet in timeline['packets']:
                packet['timestamp_us']+=epoch_ms*1000-3_000_000
            def run(command,**kwargs):
                value=b''
                if 'date' in command:value=str(time.time_ns()).encode()
                if command[-1].endswith('/ready'):value=b'ready'
                if '/event-' in command[-1]:
                    value=b'{broken' if command[-1].endswith('event-1.json') else json.dumps(
                        dict(pressed_epoch_ms=epoch_ms,pressed_end_epoch_ms=epoch_ms+1,
                             released_epoch_ms=epoch_ms+40,released_end_epoch_ms=epoch_ms+41,
                             finished_epoch_ms=epoch_ms+42)).encode()
                return subprocess.CompletedProcess(command,0,value,b'')
            class Process:
                done=False
                def poll(self):return 0 if self.done else None
                def terminate(self):self.done=True
                def kill(self):self.done=True
                def wait(self,timeout=None):self.done=True;return 0
            def popen(command,**kwargs):
                stream=kwargs['stdout']
                if 'record' in command:
                    stream.write(json.dumps({'packets':800,'sample_rate':48000,'gaps':[]})+'\n')
                    stream.flush()
                    RobustCaptureTest.write_silence(out/'stream.wav')
                    # Keep the source timeline and PCM at eight seconds.
                    with wave.open(str(out/'stream.wav'),'wb') as wav:
                        wav.setnchannels(2);wav.setsampwidth(2);wav.setframerate(48000)
                        wav.writeframes(bytes(384000*4))
                    (out/'stream.packets.json').write_text(json.dumps(timeline))
                else:
                    stream.write('OK (1 test)\n');stream.flush()
                return Process()
            argv=['run_audio_output.py','--serial','test-device','--devices-tool',str(root/'devices.py'),
                  '--output',str(out),'--apk',str(apk),'--test-apk',str(test_apk)]
            for name in ['unpressedPadIsRejected','emptyPadIsRejected','silentFileIsRejected']:
                argv+=['--case',name]
            with patch.object(sys,'argv',argv),patch.dict(os.environ,{'ANDROID_HOME':str(root)}), \
                 patch.object(runner.subprocess,'run',side_effect=run), \
                 patch.object(runner.subprocess,'Popen',side_effect=popen), \
                 patch.object(runner.time,'sleep'), \
                 patch.object(runner,'clock_bounds',return_value={
                     'offset_upper_us':1000,'offset_lower_us':-1000}):
                self.assertEqual(3,runner.main())
            results=json.loads((out/'results.json').read_text())['results']
            self.assertEqual(['passed','environment_unconfirmed','passed'],[r['status'] for r in results])

    def test_pack_mapping_bytes_are_pinned_in_apk(self):
        import zipfile
        manifest=RobustCaptureTest.manifest()
        assets=runner.ROOT/'app/src/androidTest/assets/audio-output'
        with tempfile.TemporaryDirectory() as temp:
            apk=Path(temp)/'test.apk'
            with zipfile.ZipFile(apk,'w') as archive:
                for path in assets.iterdir():
                    archive.writestr('assets/audio-output/'+path.name,
                                     b'1 1 1 mpeg.mp3\n' if path.name=='keySound' else path.read_bytes())
            with self.assertRaisesRegex(ValueError,'keySound'):
                runner.validate_fixtures(manifest,apk)


if __name__=='__main__':unittest.main()
