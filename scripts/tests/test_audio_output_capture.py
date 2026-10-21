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
            result = runner.extract_case(source,target,log,{'pressed_epoch_ms':3000,'released_epoch_ms':3100})
            self.assertAlmostEqual(.75,result['pressed_s'])
            self.assertAlmostEqual(.85,result['released_s'])
            with wave.open(str(target),'rb') as recorded:
                self.assertEqual(192000,recorded.getnframes())
                self.assertEqual(pcm[60000*4:252000*4],recorded.readframes(192000))

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
            result=runner.extract_case(source,target,log,{'pressed_epoch_ms':3000,'released_epoch_ms':3100})
            self.assertIn('environment_error',result)
            self.assertEqual([gap],result['gaps'])
            with wave.open(str(target),'rb') as recorded:
                self.assertEqual(pcm[60000*4:252000*4],recorded.readframes(192000))

    def test_refuses_missing_pre_input_postroll_and_zero_timestamp(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target = Path(temp)/'stream.wav',Path(temp)/'case.wav'
            with wave.open(str(source),'wb') as out:
                out.setnchannels(2);out.setsampwidth(2);out.setframerate(48000);out.writeframes(bytes(48000*4*5))
            for first,pressed in [(1_000_000,1100),(1_000_000,4000),(0,3000)]:
                with self.subTest(first=first,pressed=pressed),self.assertRaises(ValueError):
                    runner.extract_case(source,target,PacketTimelineTest().timeline() if first else {},
                                        {'pressed_epoch_ms':pressed,'released_epoch_ms':pressed+100})
                self.assertFalse(target.exists())


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
