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

The generic runner cannot observe `screenInputs` cases. It must report them as
`unverified`, without substituting ordinary pad calls for distinct fingers or
MIDI sources. `requiresCaseSensitiveFiles` cases must likewise stay unverified
when the filesystem cannot preserve their distinct input files. Existing
`unobserved` restrictions in the corpus remain in force.
If the host changes a filename while writing it (for example, a decomposed
Unicode name becomes composed), the parser is not called. The case stays
unverified and its actual path inventory is saved as `storedInputPaths`.

The device recorder exercises the eight screen scenarios separately:

```sh
ANDROID_SERIAL=<leased-device-id> bash scripts/ci-connected-tests.sh \
  com.kimjisub.launchpad.basefeatures.ConformanceScreenObservationTest \
  com.kimjisub.launchpad.basefeatures.ConformanceCaseSensitiveObservationTest
```

It injects distinct pointers, uses a virtual Launchpad transport with the real
driver, taps chain buttons, and starts/stops auto play on the live screen's view
model. It retains input timestamps, chain, lights, sound requests, stop keys and
currently looping sounds in `/data/local/tmp/unipad_tests/conformance-screen`.
Its audio recorder uses the native decoder but does not open a speaker.

These are raw real-time observations, not normalized conformance results. Every
screen record remains `unverified`: a completed recorder test cannot certify
virtual-clock event ordering or the auto-play option gestures. Do not replace
the generic report's missing screen observations with these snapshots or count
successful recorder checks as corpus passes. Preserve the complete output and
device files before running the tests again.

The seven file parser tests write the exact corpus files on Android
internal storage, check their stored fingerprint and distinct path inventory,
run `UniPackFolder`, and compare its normalized parse result. Their separate
records in `conformance-files/` include every stored file's hash. They can confirm
the three case-colliding and four Unicode filename cases on that device;
they do not change the generic host
report or the restrictions in the canonical corpus.
