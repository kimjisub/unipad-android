"""The output verdict must reject a silent or unrelated recording."""
import importlib.util
import math
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location('audio_output', Path(__file__).parents[1] / 'audio_output.py')
audio = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audio)


class OutputVerdictTest(unittest.TestCase):
    rate = 6000

    def tone(self, hz=431):
        return [0.2 * math.sin(2 * math.pi * hz * i / self.rate) *
                (0.4 + 0.6 * ((i // 173) % 3 != 0)) for i in range(self.rate)]

    def capture(self, signal, start=1):
        return [0.] * int(start * self.rate) + signal + [0.] * (2*self.rate)

    def verdict(self, captured, reference=None, **kw):
        return audio.compare([captured, captured], [reference or self.tone()] * 2,
                             self.rate, 1., **kw)

    def test_detects_reference_after_a_small_output_delay(self):
        result = self.verdict(self.capture(self.tone(), 1.08))
        self.assertTrue(result['passed'], result)
        self.assertAlmostEqual(result['channels'][0]['onset_s'], 1.08, delta=.002)

    def test_silence_does_not_prove_playback(self):
        self.assertFalse(self.verdict([0.] * 18000)['passed'])

    def test_another_pad_or_a_short_click_is_not_the_reference(self):
        for signal in (self.tone(673), self.tone()[:300] + [0.] * 5700):
            self.assertFalse(self.verdict(self.capture(signal))['passed'])

    def test_a_fixed_pitch_cannot_replace_a_changing_reference(self):
        reference = [.2 * math.sin(2*math.pi*(431*(i/self.rate)+37*(i/self.rate)**2))
                     for i in range(self.rate)]
        self.assertFalse(self.verdict(self.capture(self.tone()),reference)['passed'])

    def test_loud_background_is_not_twenty_decibels_quieter(self):
        signal = self.tone()
        self.assertFalse(self.verdict(signal * 4)['passed'])

    def test_previous_buffer_cannot_pass(self):
        self.assertFalse(self.verdict(self.capture(self.tone(), 0.))['passed'])

    def test_channels_are_separate(self):
        tone = self.tone()
        silence = [0.] * self.rate
        captured = [self.capture(tone), self.capture(silence)]
        self.assertTrue(audio.compare(captured, [tone, silence], self.rate, 1.)['passed'])
        self.assertFalse(audio.compare(captured[::-1], [tone, silence], self.rate, 1.)['passed'])

    def test_signal_below_absolute_floor_fails(self):
        self.assertFalse(self.verdict(self.capture([v * .001 for v in self.tone()]))['passed'])

    def test_missing_postroll_and_a_hole_in_the_tone_fail(self):
        self.assertFalse(self.verdict(self.capture(self.tone())[:12000])['passed'])
        tone = self.tone()
        tone[1500:3000] = [0.] * 1500
        self.assertFalse(self.verdict(self.capture(tone))['passed'])

    def test_both_notes_of_a_chord_are_required(self):
        chord = [a+b for a,b in zip(self.tone(),self.tone(673))]
        self.assertTrue(self.verdict(self.capture(chord), chord)['passed'])
        self.assertFalse(self.verdict(self.capture(self.tone()), chord)['passed'])

    def test_stopped_loop_must_stay_silent_after_tail(self):
        captured = [0.] * 6000 + self.tone() * 3
        self.assertFalse(audio.is_silent([captured]*2, self.rate, 2.2, 2.7)['passed'])
        self.assertTrue(audio.is_silent([[0.]*18000]*2, self.rate, 2.2, 2.7)['passed'])


if __name__ == '__main__':
    unittest.main()
