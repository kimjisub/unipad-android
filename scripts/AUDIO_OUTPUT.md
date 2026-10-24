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
| Four-channel PCM WAV mixed to stereo | `quadWavIsHeardInStereo` |
| 5.1 PCM WAV mixed to stereo | `surroundWavIsHeardInStereo` |

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

`make_audio_output_fixtures.py` creates twelve original, one-second, 48 kHz stereo
references. All use the same envelope/chirp formula, with distinct pitches per pad
and channel, a 10 ms fade, and peak amplitude 0.16. The left-only, right-only and
silent fixtures deliberately contain zero samples in specified channels. Each
channel changes in frequency and amplitude over time, so a start-up click or a
steady unrelated tone cannot match the complete reference.

`scripts/fixtures/audio-output/manifest.json` records pad coordinates (row-major,
zero-based), filenames, original reference WAVs, frequencies, hashes, exact
encoding commands and ffmpeg version. The sounds and pack mapping live in the
test APK's `assets/audio-output`. The host verifies reference and APK asset hashes, including the exact `info`
and `keySound` mapping bytes, before testing. MP3 filenames with and without ID3 tags are separate fixtures.
The last two source WAVs have four and six channels and explicit speaker masks.
The quad source repeats each reference channel in its front and rear speaker;
the 5.1 source carries the reference in the surrounds at the inverse down-mix gain.
They must reproduce their stereo references through the real decoder and mixer.
`python3 scripts/make_audio_output_fixtures.py --multichannel-only` regenerates
only these two sources, references and their mapping without re-encoding earlier files.

Full fixture authoring requires ffmpeg with MP3, experimental native Vorbis and AAC
encoders; running the detector requires only Python's standard library.

The managed recorder directory must also provide `android_clock.py`. The runner
loads its `ClockSession` for one persistent, non-PTY ADB connection per round.
Readiness completes before timing requests, so connection startup is recorded
separately rather than included in each clock sample. Three measurements on each
side of every input still bound the guest/host offset; the existing 50 ms limit
and 1 ms precision margin remain unchanged. A missing tool or broken clock
connection is environment unconfirmed, with no zero-offset or one-shot fallback.
`commands.json` retains the tool hash, startup duration, all samples and clock
errors; the connection closes during cleanup on success and failure.

## Verdict version 4

- One external stream stays open for the round. Packet preparation takes at
  most ten seconds (the manager ends a no-packet request itself); each case
  copies exactly four seconds of original PCM, starting 0.75 seconds before
  its input. Missing samples are rejected, never padded. Verdicts discard the
  first 0.2 seconds and require at least 0.5 seconds of baseline before input. Screen events are mapped to sample positions using the closest packet
  timestamp and its written frame position. A missing timeline, invalid timestamps
  or an event farther than 100 ms from a packet is environment unconfirmed.
  Both press and release use this mapping, avoiding accumulated first-packet drift.
  Screen events bracket delivery with a timestamp before and after all injected
  downs/ups. The earliest press uses the pre-delivery timestamp; the latest press
  and release use post-delivery timestamps. A 36 ms allowance on each side of
  the mapped event covers observed packet timestamp jitter.
  Any recorder-inserted gap overlapping the four-second window, including
  quiet baseline or postroll, is environment unconfirmed (exit 3). The original
  samples are retained without removing or joining gaps. Missing/invalid timing or incomplete baseline/postroll
  means environment unconfirmed. A missing packet stream is never silence.
- After two seconds of quiet preparation, the stream gets nine seconds to
  establish its packet connection, then screen commands run 2.5 seconds apart
  after each completed gesture. The manager captures at most 120 seconds;
  this runner stops it as soon as the last case has complete postroll. The
  recorder must report 16-bit stereo PCM.
  Sample rate and output gaps are retained in its raw JSON. The reference and
  capture are box-filtered and decimated to 6000 Hz; incompatible rates are
  rejected explicitly.
- RMS must be at least -50 dBFS and rise at least 20 dB over the 0.5-second
  baseline. For an exactly zero baseline only the absolute floor applies.
  Raw full-rate RMS and peak per channel are also retained by the recorder.
- Normalized time/frequency magnitude correlation must reach 0.90 independently
  in every active channel. Both reference and capture use 256-sample Hann windows
  at 6000 Hz with a 120-sample hop (about 43 ms windows, 20 ms steps). This keeps
  pitch changes and amplitude envelopes while tolerating lossy-decoder phase
  and short stream timing shifts. A quarter-second hole, another pitch, a click,
  a missing chord note and swapped channels are regression-tested as failures. The central 0.90 seconds of the original reference is
  searched within 0 to 1.0 seconds after input, allowing decoder and output
  buffering without accepting a previous voice. The first measured WAV output
  started about 0.36 seconds after input. Initial time/frequency similarities
  ranged from 0.91 to 0.999 for normal signals; a different pad scored below 0.07.
  Earlier sample-phase correlation rejected several normal recordings and its
  failed rounds are retained in the evidence.
- Silent channels and silence controls must stay below -65 dBFS through the
  whole possible output window: 2.5 seconds after input, including permitted
  output delay, the one-second signal and postroll. A loop must match its first second
  and be below -65 dBFS for 0.5 seconds starting 0.6 seconds after the latest
  possible release. If that complete interval extends beyond the four-second
  recording, the case is environment unconfirmed, never an app failure. The first measured stop tail was
  0.46 seconds; every round also reports its last non-silent 10 ms window.
  The postroll must cover 0.5 seconds after the latest permitted end of a normal
  sound. Signal thresholds are versioned constants in `audio_output.py`. The packet
  timing allowance and per-case loop stop window are declared in `run_audio_output.py`.

Exit 0 means every requested case passed. Exit 2 means a confirmed recording
rejected the expected signal or a negative control failed. Exit 3 means the
screen/recorder environment was unconfirmed. Both nonzero exits fail automation;
XML represents unconfirmed cases as errors, never skips or passes. Preparation
failures (including missing SDK variables or APKs), empty/malformed recorder
output, and cleanup timeouts also leave both reports with exit 3. A corrupt
case WAV or other per-case exception does not replace other case verdicts.
If an input command fails or its response times out, collection stops: the
pending input could otherwise play in a later case. Completed captures are
retained and every uncollected requested case is reported as environment
unconfirmed. A response received on time but containing malformed JSON stays
a per-case error.
An unwritable or nonempty evidence directory and invalid command-line options
remain invocation errors; no previous evidence is overwritten.

Every round stores the original recorder stdout, continuous source WAV and
byte-for-byte four-second case WAVs, `stream.packets.json`, per-case gap records,
packet-mapped input/release positions, channel metrics,
correlations, input events, commands, exit codes, source commit and tracked diff,
APK/test APK hashes, fixture manifest, device serial, OS properties, emulator
version, instrumentation output and XML. Keep the source checkout clean for
final evidence. Attach archives to the review before a rerun overwrites
anything. Logs and result files belong outside the build tree.

Case rules, input pad indices, reference pads, alternate-pad controls and loop
stop windows are declared together in `CASES`. The screen accepts pad indices
and hold duration as JSON; it does not infer test semantics from action strings.

This proves digital output of this emulator and test pack. It does not prove
physical speaker/headphone/Bluetooth delivery, sound quality, playing latency,
the pre-API-29 path, all packs, or worldwide device coverage.


### Host and guest clock alignment (verdict version 3)

Each command is bracketed by three `adb shell date +%s%N` round trips before
input and three after the event file arrives. `commands.json` retains the host
wall-clock bounds and monotonic duration; the raw guest samples stay in each
event's `clock`. The intersected guest-minus-host offset interval includes a
1 ms guest timestamp quantization allowance. Empty intersections, unbracketed
events, host clock steps or unresolved interval width above 50 ms are environment
errors, not app failures. Large absolute offsets are corrected rather than
assuming that guest and host clocks agree.

The original four-second PCM window starts 0.75 seconds before the earliest
possible host press. The baseline ends at that earliest press; matching and
silence postroll include the packet-mapped input uncertainty. The host clock interval is at most
50 ms; its width in PCM can differ slightly with packet timestamp drift. The
whole mapped uncertainty and postroll must fit in the original four-second
window, otherwise the case is environmentally unconfirmed. Loop
stop checks start from the latest possible release. Corrected bounds, offset
and uncertainty remain in the capture record. No samples are shifted or resampled
to align clocks.

`differentPadIsRejected` requires both rejection of pad 0 and a positive match
of the actual pad 1 signal, including its RMS floor and baseline rise. Silence,
a third sound and an inaudibly quiet alternate cannot pass this control.
