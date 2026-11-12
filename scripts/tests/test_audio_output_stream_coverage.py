"""Recorded packet schedules distinguish usable windows from missing output."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import wave

sys.path.insert(0, str(Path(__file__).parents[1]))
import run_audio_output as runner


def recorded_timeline(fixture):
    timestamp, position = 1_000_000, 0
    packets = []
    for delta, frames in fixture['packets']:
        timestamp += delta
        packets.append({'timestamp_us': timestamp, 'frames': frames, 'at_frame': position})
        position += frames
    return {'sample_rate': 48000, 'channels': 2, 'gaps': [], 'packets': packets}


class StreamCoverageTest(unittest.TestCase):
    def extract_uniform_stream(self, directory, frames_per_packet, press_ms=3000):
        source, target = Path(directory) / 'stream.wav', Path(directory) / 'case.wav'
        log = {'sample_rate': 48000, 'channels': 2, 'gaps': [], 'packets': [
            {'at_frame': i * frames_per_packet, 'frames': frames_per_packet,
             'timestamp_us': 1_000_000 + i * 12_500} for i in range(800)]}
        with wave.open(str(source), 'wb') as wav:
            wav.setnchannels(2)
            wav.setsampwidth(2)
            wav.setframerate(48000)
            wav.writeframes(bytes(frames_per_packet * 800 * 4))
        def clock(host):
            return {'host_before_us': host - 1, 'host_after_us': host + 1,
                    'guest_us': host, 'monotonic_elapsed_us': 2}
        event = {'pressed_epoch_ms': press_ms, 'pressed_end_epoch_ms': press_ms,
                 'released_epoch_ms': press_ms + 100, 'released_end_epoch_ms': press_ms + 100,
                 'finished_epoch_ms': press_ms + 101,
                 'clock': {'before': [clock((press_ms - 1000) * 1000)],
                           'after': [clock((press_ms + 1000) * 1000)]}}
        return runner.extract_case(source, target, log, event)

    def test_boundary_applies_even_to_silent_pcm(self):
        # 504 samples per 12.5 ms is exactly 84%; 503 samples is below it.
        for frames, eligible in [(504, True), (503, False), (480, False), (620, False)]:
            with self.subTest(frames=frames), tempfile.TemporaryDirectory() as temp:
                result = self.extract_uniform_stream(temp, frames)
                self.assertEqual(eligible, 'environment_error' not in result, result)
                self.assertAlmostEqual(frames / 600, result['stream']['delivered_ratio'])

    def test_missing_postroll_only_blames_short_stream_when_whole_ratio_is_low(self):
        for frames, short_stream in [(600, False), (504, False), (503, True)]:
            with self.subTest(frames=frames), tempfile.TemporaryDirectory() as temp:
                with self.assertRaisesRegex(ValueError, 'outside recorded PCM') as raised:
                    self.extract_uniform_stream(temp, frames, press_ms=10500)
                self.assertEqual(short_stream, 'shortening the recording' in str(raised.exception))

    def test_recorded_windows_keep_passing_rounds_and_reject_short_streams(self):
        fixtures = Path(__file__).parent / 'fixtures/audio-stream-coverage'
        paths = sorted(fixtures.glob('*.json'))
        self.assertEqual(5, len(paths))
        for path in paths:
            fixture = json.loads(path.read_text())
            log = recorded_timeline(fixture)
            runner.validate_timeline(log)
            with tempfile.TemporaryDirectory() as temp:
                source, target = Path(temp) / 'stream.wav', Path(temp) / 'case.wav'
                frames = sum(p['frames'] for p in log['packets'])
                with wave.open(str(source), 'wb') as wav:
                    wav.setnchannels(2)
                    wav.setsampwidth(2)
                    wav.setframerate(48000)
                    wav.writeframes(bytes(frames * 4))
                for case in fixture['cases']:
                    with self.subTest(recording=path.stem, case=case['name']):
                        if path.stem.startswith('android-12') or (
                                path.stem.startswith('android-17') and
                                case['name'] == 'unpressedPadIsRejected'):
                            with self.assertRaisesRegex(ValueError, 'outside recorded PCM.*shortening the recording'):
                                runner.extract_case(source, target, log, case['event'])
                            self.assertFalse(target.exists())
                        else:
                            result = runner.extract_case(source, target, log, case['event'])
                            self.assertEqual(fixture['expected_eligible'],
                                             'environment_error' not in result, result)
                            self.assertIn('delivered_ratio', result['stream'])
                            target.unlink()

    def test_ratio_measures_samples_after_first_packet(self):
        log = {'sample_rate': 48000, 'packets': [
            {'at_frame': 0, 'frames': 480, 'timestamp_us': 1_000_000},
            {'at_frame': 480, 'frames': 960, 'timestamp_us': 1_025_000},
            {'at_frame': 1440, 'frames': 480, 'timestamp_us': 1_037_500},
        ]}
        stream = runner.stream_shortfall(log, 0, 1920)
        self.assertAlmostEqual(.8, stream['delivered_ratio'])
        self.assertAlmostEqual(.0375, stream['stream_seconds'])
        self.assertAlmostEqual(.03, stream['delivered_seconds'])


if __name__ == '__main__':
    unittest.main()
