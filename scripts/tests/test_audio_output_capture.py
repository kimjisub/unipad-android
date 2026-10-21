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
            result = runner.extract_case(source,target,1_000_000,3000)
            self.assertEqual(2_250_000,result['first_sample_epoch_us'])
            with wave.open(str(target),'rb') as recorded:
                self.assertEqual(192000,recorded.getnframes())
                self.assertEqual(pcm[60000*4:252000*4],recorded.readframes(192000))

    def test_refuses_missing_pre_input_postroll_and_zero_timestamp(self):
        with tempfile.TemporaryDirectory() as temp:
            source,target = Path(temp)/'stream.wav',Path(temp)/'case.wav'
            with wave.open(str(source),'wb') as out:
                out.setnchannels(2);out.setsampwidth(2);out.setframerate(48000);out.writeframes(bytes(48000*4*5))
            for first,pressed in [(1_000_000,1100),(1_000_000,4000),(0,3000)]:
                with self.subTest(first=first,pressed=pressed),self.assertRaises(ValueError):
                    runner.extract_case(source,target,first,pressed)
                self.assertFalse(target.exists())


if __name__ == '__main__': unittest.main()
