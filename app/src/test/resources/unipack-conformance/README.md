# Shared UniPack corpus

`corpus.json` and `corpus.sha256` are byte-for-byte copies of the web repository's
`meta/unipack-conformance` directory. Update both with its `sync.mjs`; do not edit
expectations or add Android exceptions in this copy.

The playback runner advances main-coroutine delays with its scenario clock, so a
slow host cannot deliver a 100 ms chain move inside a 20 ms checkpoint.

Run the parser and virtual-clock playback observations:

```sh
UNIPACK_CONFORMANCE_OUT="$PWD/build/unipack-conformance" \
  ./gradlew :app:testDebugUnitTest \
  --tests 'com.kimjisub.launchpad.unipack.conformance.*' \
  --rerun --no-build-cache --no-daemon --console=plain
python3 scripts/check_unipack_conformance.py build/unipack-conformance/android.json
```

The independent report check rejects missing and duplicate cases, unknown IDs,
changed fingerprints, observations that do not support their claimed verdict,
and record-only results. `expectedFailing` is a prediction, not an exemption:
an unexpected app result continues to fail the checks. In particular, KS-B08 and
KL-BE10 retain the shared `-2147483649` expectation.
The playback recorder reports effective repeats as the native mixer consumes them:
`-1` is infinite, positive counts repeat, and values below `-1` play once (`0`).
The parser report still preserves the raw parsed repeat value.

`screenInputs` cases run through the play screen's own input path on the same virtual clock: each
finger is a pointer of a `PadTouchTracker` wired to the view model as `PlayActivity` wires it, the
Launchpad's pads reach `controllerPadTouch` as the screen's MIDI controller passes them, chains are
chosen with the chain buttons' `selectChain`, auto play is switched with the mode buttons'
`switchPlayMode`, and auto play's presses and releases go through the screen's own auto-play listener,
which drops commands from a runner the screen has stopped or replaced. Their results name
`"inputAdapter": "play-screen"`; the report check accepts a screen-input observation only with it, and
refuses it on any other case. Distinct `input` names outside these cases are rejected. What this path
does not include: Compose's pointer events (a finger enters the tracker at its pad's centre) and the
MIDI driver's packet decoding (a pad enters at `controllerPadTouch`).
`requiresCaseSensitiveFiles` cases must stay unverified when the filesystem cannot preserve their
distinct input files. Existing `unobserved` restrictions in the corpus remain in force.
If the host changes a filename while writing it (for example, a decomposed
Unicode name becomes composed), the parser is not called. The case stays
unverified and its actual path inventory is saved as `storedInputPaths`.

Results are taken with Pro light mode on. The play screen starts with it off, which hides a keyLED
file's round-button and logo lights on the screen and on the Launchpad (both read the same light).
`ProLightModeOffTest` runs every playback case that lights them both ways and checks that off hides
exactly those lights and keeps the pad lights; it writes both results to `android-pro-light-off.json`.

The device recorder also exercises the eight screen scenarios with real input:

```sh
ANDROID_SERIAL=<leased-device-id> bash scripts/ci-connected-tests.sh \
  com.kimjisub.launchpad.basefeatures.ConformanceScreenObservationTest \
  com.kimjisub.launchpad.basefeatures.ConformanceFileObservationTest
```

It injects distinct pointers, uses a virtual Launchpad transport with the real
driver, taps chain buttons, and starts/stops auto play with the screen option. It retains input timestamps, chain, lights, sound requests, stop keys and
currently looping sounds in `/data/local/tmp/unipad_tests/conformance-screen`.
It also keeps every play and stop request and every change of a pad's pack
light and press light in the order the app made them; each snapshot holds how
many of each had happened by then. The corpus reports pack lights with press
feedback off, so RUN-G617 and RUN-G618 are recorded a second time with it on
(`<id>-press-light.json`) to show when each pad's press light ends.
Its audio recorder uses the native decoder but does not open a speaker.

These are raw real-time observations, not normalized conformance results, and never enter the
report: real-time snapshots cannot certify the corpus's virtual-clock event ordering.
Opening options while a finger is held replaces the injected gesture. That
case saves partial evidence with `completed=false` and an explicit limitation;
it does not invent a later release of the cancelled finger. Preserve the complete output and
device files before running the tests again.

The fifteen file tests write the exact corpus files on Android internal storage, which keeps
names that differ only in letter case or in composed and decomposed spelling apart (a macOS disk
merges the latter even when it is case-sensitive). They check the stored fingerprint and distinct
path inventory, then read the files with the real parser: KL-026, KL-028 and KS-G617 to KS-G619 in
the host harness's parse form, and the ten sound-identity cases through the production archive
extractor, decoder and sound runner, reporting the selected original ZIP entry and SHA-256. Each
case leaves `conformance-files/<id>.json` with its `actual`, its status against the corpus and
every stored file's hash.

Those records fill the host report's gaps:

```sh
adb -s <leased-device-id> pull /data/local/tmp/unipad_tests/conformance-files device/
python3 scripts/merge_device_conformance.py build/unipack-conformance/android.json \
  device/conformance-files -o build/unipack-conformance/android-merged.json
```

A record is merged only when it ran the same corpus and input, comes from the recorder for that kind
of case, kept every name and byte apart, and its status agrees with its actual value; otherwise the
case stays unverified with the reason. The merged report names the device and each record's SHA-256,
and `check_unipack_conformance.py` accepts a device observation only for a case the host cannot see.

The audio operation adapter calls `AudioSafety.cost`, `AudioFileInspector.inspect`
and `AudioSelector.select` for 115 cases. Physical WAV fixtures use real PCM
headers and data lengths, rather than the input's claimed duration. Each result
names its `operationAdapter`; the independent checker refuses operations passed
through the ordinary parser. Neither adapter reads an expected result.

The mapper adapter invokes `UniPackAutoMapper.run`, then reopens its actual output
through the app parser. Integer and fractional duration fixtures enter the app's
`SoundDurationReader` unchanged; rounding is performed by the real mapper. Any observed mapper
difference still fails the suite, even if `expectedFailing` did not predict it.
