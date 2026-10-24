"""Loading preparation must be observed before judging the next audible turn."""
import copy
from pathlib import Path
import tempfile
import unittest
import wave

import test_audio_output_robustness as robustness

runner = robustness.runner


class LoadingOutputTest(unittest.TestCase):
    def judge(self, pad, preparation):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            rule = runner.LOADING_CASE
            name = rule['name']
            reference = runner.ROOT / robustness.RobustCaptureTest.manifest()['pads'][pad]['reference']
            with wave.open(str(reference), 'rb') as source:
                pcm = source.readframes(source.getnframes())
            with wave.open(str(out / (name + '.wav')), 'wb') as target:
                target.setnchannels(2)
                target.setsampwidth(2)
                target.setframerate(48000)
                target.writeframes(bytes(36000 * 4) + pcm + bytes(108000 * 4))
            record = robustness.RobustCaptureTest.record(name)
            record['case'] = rule
            if preparation is not None:
                record['preparation'] = preparation
            return runner.judge_record(record, robustness.RobustCaptureTest.manifest(), out)

    def preparation(self):
        return {'loading_before': True, 'loading_after': False,
                'silent_press_count': 1, 'runtime_key_sound': runner.LOADING_KEY_SOUND}

    def test_second_reference_passes_and_first_reference_is_rejected(self):
        correct = self.judge(6, self.preparation())
        wrong = self.judge(0, self.preparation())
        self.assertEqual('passed', correct['status'], correct)
        self.assertFalse(correct['first_turn_verdict']['passed'])
        self.assertEqual('failed', wrong['status'], wrong)
        self.assertTrue(wrong['first_turn_verdict']['passed'])

    def test_missing_or_wrong_preparation_is_unconfirmed_even_with_correct_pcm(self):
        for field, value in [('loading_before', False), ('loading_after', True),
                             ('silent_press_count', 0), ('runtime_key_sound', 'changed')]:
            preparation = copy.deepcopy(self.preparation())
            preparation[field] = value
            with self.subTest(field=field):
                self.assertEqual('environment_unconfirmed', self.judge(6, preparation)['status'])
        self.assertEqual('environment_unconfirmed', self.judge(6, None)['status'])
