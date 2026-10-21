"""Case extraction must retain original samples and reject missing timing."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
import wave

sys.path.insert(0,str(Path(__file__).parents[1]))
SPEC = importlib.util.spec_from_file_location('run_audio_output',Path(__file__).parents[1]/'run_audio_output.py')
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class CaptureWindowTest(unittest.TestCase):
    def test_extracts_original_pcm_without_padding_or_rescaling(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target = Path(temp)/'stream.wav',Path(temp)/'case.wav'
            pcm = bytes(range(256))*6000  # eight seconds at 48 kHz stereo 16-bit
            with wave.open(str(source),'wb') as out:
                out.setnchannels(2);out.setsampwidth(2);out.setframerate(48000);out.writeframes(pcm)
            log=PacketTimelineTest().timeline()
            result = runner.extract_case(source,target,log,{'pressed_epoch_ms':3000,'released_epoch_ms':3100,'finished_epoch_ms':3101,
                'clock':ClockAndControlTest.clock(offset=0,rtt=2)})
            self.assertAlmostEqual(.75,result['pressed_s'],delta=1/48000)
            self.assertAlmostEqual(.852,result['released_s'],delta=1/48000)
            with wave.open(str(target),'rb') as recorded:
                self.assertEqual(192000,recorded.getnframes())
                self.assertEqual(pcm[59952*4:251952*4],recorded.readframes(192000))

    def test_preserves_gap_samples_and_marks_extracted_case_unconfirmed(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target = Path(temp)/'stream.wav',Path(temp)/'case.wav'
            log=PacketTimelineTest().timeline()
            gap={'at_frame':120000,'frames':4800}
            log['gaps']=[gap]
            for packet in log['packets'][250:]:packet['at_frame']+=4800
            pcm=b'\x01\x00'*2*120000+bytes(4800*4)+b'\x01\x00'*2*(384000-120000)
            with wave.open(str(source),'wb') as out:
                out.setnchannels(2);out.setsampwidth(2);out.setframerate(48000);out.writeframes(pcm)
            result=runner.extract_case(source,target,log,{'pressed_epoch_ms':3000,'released_epoch_ms':3100,'finished_epoch_ms':3101,
                'clock':ClockAndControlTest.clock(offset=0,rtt=2)})
            self.assertIn('environment_error',result)
            self.assertEqual([gap],result['gaps'])
            with wave.open(str(target),'rb') as recorded:
                self.assertEqual(pcm[59952*4:251952*4],recorded.readframes(192000))

    def test_refuses_missing_pre_input_postroll_and_zero_timestamp(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target = Path(temp)/'stream.wav',Path(temp)/'case.wav'
            with wave.open(str(source),'wb') as out:
                out.setnchannels(2);out.setsampwidth(2);out.setframerate(48000);out.writeframes(bytes(48000*4*5))
            clock=ClockAndControlTest.clock(offset=0,rtt=2)
            clock['before'][0].update(host_before_us=499999,host_after_us=500001,guest_us=500000)
            clock['after'][0].update(host_before_us=4499999,host_after_us=4500001,guest_us=4500000)
            for first,pressed in [(1_000_000,1100),(1_000_000,4000),(0,3000)]:
                with self.subTest(first=first,pressed=pressed),self.assertRaises(ValueError):
                    runner.extract_case(source,target,PacketTimelineTest().timeline() if first else {},
                                        {'pressed_epoch_ms':pressed,'released_epoch_ms':pressed+100,
                                         'finished_epoch_ms':pressed+101,'clock':clock})
                self.assertFalse(target.exists())


class ClockAndControlTest(unittest.TestCase):
    @staticmethod
    def clock(offset=300_000, rtt=2000):
        def sample(host):
            return {'host_before_us':host-rtt//2,'host_after_us':host+rtt//2,
                    'guest_us':host+offset,'monotonic_elapsed_us':rtt}
        return {'before':[sample(2_000_000)],'after':[sample(4_000_000)]}

    def test_guest_clock_offset_does_not_move_the_original_input_window(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target=Path(temp)/'stream.wav',Path(temp)/'case.wav'
            with wave.open(str(source),'wb') as out:
                out.setnchannels(2);out.setsampwidth(2);out.setframerate(48000)
                out.writeframes(bytes(384000*4))
            for offset in [300_000,-300_000]:
                event={'pressed_epoch_ms':3000+offset/1000,'released_epoch_ms':3100+offset/1000,
                       'finished_epoch_ms':3101+offset/1000,'clock':self.clock(offset)}
                result=runner.extract_case(source,target,PacketTimelineTest().timeline(),event)
                self.assertAlmostEqual(59904,result['source_frame_offset'],delta=1)
                self.assertLessEqual(result['clock']['uncertainty_us'],4000)

    def test_clock_correction_keeps_normal_sound_out_of_baseline(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target=Path(temp)/'stream.wav',Path(temp)/'case.wav'
            reference=runner.ROOT/'scripts/fixtures/audio-output/pad-0.wav'
            with wave.open(str(reference),'rb') as wav: tone=wav.readframes(48000)
            pcm=bytes(98400*4)+tone+bytes((384000-98400-48000)*4)
            with wave.open(str(source),'wb') as wav:
                wav.setnchannels(2);wav.setsampwidth(2);wav.setframerate(48000);wav.writeframes(pcm)
            # Actual press is host epoch 3s; guest clock is half a second ahead.
            result=runner.extract_case(source,target,PacketTimelineTest().timeline(),
                {'pressed_epoch_ms':3500,'released_epoch_ms':3540,'finished_epoch_ms':3541,
                 'clock':self.clock(offset=500_000)})
            signal=runner.read_wav(source);expected=runner.read_wav(reference)
            self.assertFalse(runner.compare(signal,expected,runner.RATE,2.5)['passed'])
            self.assertTrue(runner.compare(runner.read_wav(target),expected,runner.RATE,
                result['pressed_s'],result['input_uncertainty_s'])['passed'])

    def test_unmeasured_imprecise_or_jumping_clock_is_environment_unconfirmed(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target=Path(temp)/'stream.wav',Path(temp)/'case.wav'
            with wave.open(str(source),'wb') as out:
                out.setnchannels(2);out.setsampwidth(2);out.setframerate(48000)
                out.writeframes(bytes(384000*4))
            jumping=self.clock();jumping['after'][0]['guest_us']+=500_000
            changed_host=self.clock();changed_host['after'][0]['host_after_us']+=500_000
            for clock in [None,self.clock(rtt=200_000),jumping,changed_host]:
                event={'pressed_epoch_ms':3300,'released_epoch_ms':3400,
                       'finished_epoch_ms':3401,'clock':clock}
                with self.subTest(clock=clock),self.assertRaises(ValueError):
                    runner.extract_case(source,target,PacketTimelineTest().timeline(),event)

    def test_wrong_pad_control_requires_audible_matching_alternate(self):
        import math
        tone=lambda hz:[.2*math.sin(2*math.pi*hz*i/runner.RATE) for i in range(runner.RATE)]
        expected=[tone(431)]*2;actual=[tone(673)]*2
        capture=lambda note:[[0.]*6000+c+[0.]*12000 for c in note]
        self.assertTrue(runner.different_pad_passed(capture(actual),expected,actual,1.))
        for sound in [[[0.]*6000]*2,expected,[tone(919)]*2,
                      [[v*.001 for v in c] for c in actual]]:
            with self.subTest(sound=sound[0][:2]):
                self.assertFalse(runner.different_pad_passed(capture(sound),expected,actual,1.))


class PacketTimelineTest(unittest.TestCase):
    def timeline(self):
        return {'sample_rate':48000,'channels':2,'gaps':[],
                'packets':[{'timestamp_us':1_000_000+i*10_000,'received_epoch_us':1_001_000+i*10_000,
                            'frames':480,'at_frame':i*480,'index':i} for i in range(800)]}

    def test_maps_using_nearby_packet_instead_of_first_timestamp(self):
        log=self.timeline()
        # Accumulated sub-threshold drift must not shift later screen input.
        for packet in log['packets'][400:]: packet['timestamp_us'] += 50_000
        self.assertAlmostEqual(240000,runner.event_frame(log,6050),delta=1)

    def test_rejects_missing_log_invalid_timestamps_and_sparse_packets(self):
        for invalid in [{}, {'sample_rate':48000,'channels':2,'gaps':[],'packets':[]}]:
            with self.assertRaises(ValueError):runner.event_frame(invalid,3000)
        for key,value in [('timestamp_us',0),('frames',0),('at_frame',-1)]:
            log=self.timeline();log['packets'][0][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):runner.event_frame(log,3000)
        log=self.timeline()
        for packet in log['packets']:packet['frames']=34
        with self.assertRaises(ValueError):runner.event_frame(log,3000)

    def test_missing_recording_intervals_are_not_extrapolated(self):
        log=self.timeline()
        for event in [500,10000]:
            with self.assertRaises(ValueError):runner.event_frame(log,event)

    def test_any_inserted_gap_overlapping_case_is_unconfirmed_even_when_quiet(self):
        log=self.timeline()
        start,end=60000,252000
        for at,length,overlaps in [(start,1,True),(start-1,2,True),(end-1,1,True),
                                    (end,1,False),(start-1,1,False)]:
            log['gaps']=[{'at_frame':at,'frames':length}]
            self.assertEqual(overlaps,bool(runner.overlapping_gaps(log,start,end)))


if __name__ == '__main__': unittest.main()
