# External emulator audio checks

This suite follows a real screen touch through the normal pack loader, Android
decoder, SoundRunner and Oboe stream to the emulator's external `streamAudio` PCM.
It adds no production hooks. The recorder never plays the fixture itself.

Use a dedicated debug installation and an emulator rented with audio capture
support. Supply the serial returned by the device manager and its `devices.py`
path; authentication stays inside that manager. The caller must return the device
as soon as testing finishes. Neither this script nor Gradle starts an emulator.

```sh
python3 scripts/run_audio_output.py \
  --serial "$ANDROID_SERIAL" --devices-tool "$DEVICE_MANAGER" \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --output "$EVIDENCE_DIRECTORY/normal"
```

An output directory must be empty. Save and attach a round, including failures,
before another round. `--case NAME` selects a case; repeat that flag to select
several. A full run contains these stable result names, in
`TEST-EmulatorOutput.xml` and `results.json`:

| Grammar item | Required output test |
| --- | --- |
| A-01, PCM WAV | `wavIsHeardAtTheEmulatorOutput` |
| A-02, MP3 | `mp3IsHeardAtTheEmulatorOutput` |
| A-03, OGG Vorbis | `oggIsHeardAtTheEmulatorOutput` |
| A-04, AAC in M4A | `m4aIsHeardAtTheEmulatorOutput` |
| A-05, MP3 with a WAV filename | `bareMp3NamedWavIsHeardAtTheEmulatorOutput` and `taggedMp3NamedWavIsHeardAtTheEmulatorOutput` |

The remaining cases check separated left/right channels, no press, an empty
pressed pad, a silent file, a different pad, a held loop's release, and two pads
together. Negative controls pass only when the expected waveform is rejected;
no-press, empty-pad and silent-file controls must also remain below the silence
threshold. They never count as evidence of audible output.

## Detector self-check

First run the WAV case with `--block-playback`: the test APK suppresses only
`Engine.play` while retaining the real decoder and continuously open Oboe
stream. The external output test must fail (exit 2). Attach that entire round.
Then repeat without the flag: it must pass (exit 0). Screen injection and
instrumentation success alone do not constitute an audio pass. The injection
binding is unloaded on every exit, and no production code is changed.

The ordinary connected suite runs `EmulatorOutputScreenTest` without a host
session. It loads this same fixed pack and injects a screen touch; it does not
claim to verify external audio. External audio results come only from this host
runner. Run the host suite separately before publishing changes affecting sound.

## Original fixtures

`make_audio_output_fixtures.py` creates ten original, one-second, 48 kHz stereo
signals. All use the same envelope/chirp formula, with distinct pitches per pad
and channel, a 10 ms fade, and peak amplitude 0.16. The left-only, right-only and
silent fixtures deliberately contain zero samples in specified channels. Each
channel changes in frequency and amplitude over time, so a start-up click or a
steady unrelated tone cannot match the complete reference.

`scripts/fixtures/audio-output/manifest.json` records pad coordinates (row-major,
zero-based), filenames, original reference WAVs, frequencies, hashes, exact
encoding commands and ffmpeg version. The sounds and pack mapping live in the
test APK's `assets/audio-output`. The host verifies reference and APK asset hashes
before testing. MP3 filenames with and without ID3 tags are separate fixtures.
Fixture authoring requires ffmpeg with MP3, experimental native Vorbis and AAC
encoders; running the detector requires only Python's standard library.

## Verdict version 1

- Each capture requests four seconds, waits at most ten seconds, discards the
  first 0.2 seconds for verdicts, and requires at least 0.5 seconds of baseline
  before the recorded screen input. The screen event and first output packet
  use epoch timestamps. Missing/invalid timing or incomplete baseline/postroll
  means environment unconfirmed. A missing packet stream is never silence.
- Input is issued about one second after starting recording, following two
  seconds of quiet preparation. The recorder must report 16-bit stereo PCM.
  Sample rate and output gaps are retained in its raw JSON. The reference and
  capture are box-filtered and decimated to 24000 Hz; incompatible rates are
  rejected explicitly.
- RMS must be at least -50 dBFS and rise at least 20 dB over the 0.5-second
  baseline. For an exactly zero baseline only the absolute floor applies.
  Raw full-rate RMS and peak per channel are also retained by the recorder.
- Normalized signed waveform correlation must reach 0.92 independently in
  every active channel. The central 0.90 seconds of the original reference is
  searched within 0 to 0.5 seconds after input, allowing decoder and output
  buffering without accepting a previous voice. The first measured WAV output
  started about 0.36 seconds after input and matched at approximately 0.99.
- Silent channels must stay below -65 dBFS. A loop must match its first second
  and be below -65 dBFS for 0.5 seconds starting 0.2 seconds after release.
  The postroll must cover 0.5 seconds after the latest permitted end of a normal
  sound. Thresholds are versioned constants in `audio_output.py`.

Exit 0 means every requested case passed. Exit 2 means a confirmed recording
rejected the expected signal or a negative control failed. Exit 3 means the
screen/recorder environment was unconfirmed. Both nonzero exits fail automation;
XML represents unconfirmed cases as errors, never skips or passes.

Every round stores the original recorder stdout, WAVs, channel metrics,
correlations, input events, commands, exit codes, source commit and tracked diff,
APK/test APK hashes, fixture manifest, device serial, OS properties, emulator
version, instrumentation output and XML. Keep the source checkout clean for
final evidence. Archives must be uploaded to the task before a rerun overwrites
anything. Logs and result files belong outside the build tree.

This proves digital output of this emulator and test pack. It does not prove
physical speaker/headphone/Bluetooth delivery, sound quality, playing latency,
the pre-API-29 path, all packs, or worldwide device coverage.
