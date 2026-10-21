# Native pack grammar evidence

Run on a device leased by your device manager, setting `ANDROID_SERIAL` to its exact
serial. Use an output directory outside the checkout, or a Git-ignored directory,
so generated evidence is not mistaken for a source change. The output must be
separate from `.ci-results`, `app/build/test-results/testDebugUnitTest`, and
`app/build/outputs/androidTest-results/connected`: it cannot equal, contain, or
sit inside any of those result sources. Relative paths, absolute paths and
symlink targets are checked before creating output or running any commands.
For example, `.ci-results/round` is rejected even though Git ignores it; a new
directory outside the checkout is recommended. Supply the grammar
version and immutable revision that was reviewed:

```sh
python3 scripts/native_grammar_results.py \
  --grammar-version <version> --grammar-revision <revision> \
  --output <new-result-directory>
```

The source must be committed and clean to verify an item. The runner executes the
existing archive unit tests with Gradle `--rerun` (bypassing up-to-date and cached
results for that test task) and the selected device tests, copies original XML
before the next command can overwrite it, and writes `results.json` automatically.
The result includes the actual Git commit, fixture hashes, device fingerprint/API/ABI,
command arguments and exit codes, timestamps, and hashes of the original results.
It refuses to overwrite an existing round. Preserve and upload each complete round
before running another one. A command failure does not become a passing item.

The required test manifest is `REQUIRED` in `native_grammar_results.py`, independent
of the discovered results. Every listed test must occur exactly once and pass.
Missing, skipped, duplicate or failed tests cannot verify an item. The shared
conformance reporter is unchanged; this is an independent native evidence input.

To include real external output, lease an audio-enabled emulator, then add:

```sh
python3 scripts/native_grammar_results.py \
  --grammar-version <version> --grammar-revision <revision> \
  --with-audio-output --devices-tool <managed-devices.py> \
  --output <new-result-directory>
```

This builds both debug APKs and runs the full existing `run_audio_output.py` suite
on the same `ANDROID_SERIAL`. It preserves that entire round separately under
`raw/audio-output`, including XML, continuous and per-case WAVs, packet timeline,
input clocks, metadata, commands, exit codes and logs. `rawFiles` hashes every
original file. The source commit and status before and after are also retained.
The output round's source commit must match the native round, its tracked diff and
status must be empty, and its device must match. Missing raw files, missing or
duplicate required cases, failed or unconfirmed output, and nonzero command exits
cannot produce a passing audio item. Output names found in ordinary unit/device
XML are ignored; only the separately executed host round can supply them.

| Item | Same expected behavior | Required evidence |
| --- | --- | --- |
| F-01 | Find all five root names without regard to case; reject `info.txt` and `keySounds` | `PackRootNamesDeviceTest`: three tests, each first proving case sensitivity in the pack's own internal directory; uppercase names then load actual sound, LED and autoplay data |
| F-05 | Find the unique pack through wrappers, adjacent files, dot files and `__MACOSX` | Five existing `FileManagerTest` scenarios plus real CP949 archive import with two wrappers, readmes, dot files and resource-fork metadata |
| F-06 | UTF-8 with and without its flag, then unflagged CP949 | Three existing `PackArchiveTest` cases plus the device CP949 capability and real importer tests; installed Korean sound paths and bytes checked |
| A-01 | PCM WAV sound | Decoder, pad-to-native voice, and `EmulatorOutput#wavIsHeardAtTheEmulatorOutput` |
| A-02 | MP3 sound | Decoder, pad-to-native voice, and `EmulatorOutput#mp3IsHeardAtTheEmulatorOutput` |
| A-03 | OGG Vorbis sound | Decoder, pad-to-native voice, and `EmulatorOutput#oggIsHeardAtTheEmulatorOutput` |
| A-04 | AAC in M4A sound | Decoder, pad-to-native voice, and `EmulatorOutput#m4aIsHeardAtTheEmulatorOutput` |
| A-05 | MP3 bytes named `.wav`, with and without an ID3 tag | Both decoder and both pad-to-native voice tests, plus `EmulatorOutput#bareMp3NamedWavIsHeardAtTheEmulatorOutput` and `EmulatorOutput#taggedMp3NamedWavIsHeardAtTheEmulatorOutput` |

The audio observer forwards **all** operations to `SoundRunner.OboeEngine`.
It checks that the real decoder's nonzero PCM object reaches native loading unchanged,
that an empty pad stays silent, and that pad (2, 3) plays that loaded native sound ID
with a valid native voice key, full volume and one-shot playback. It does not substitute
PCM, sound IDs or the playback engine.

Without `--with-audio-output`, audio items remain **unverified**. Decoder PCM, buffer
identity and native voice acceptance alone cannot detect a silent native mixer or
a muted output device. With the option, the existing detector checks the emulator's
external digital output against its pinned references, with unchanged thresholds
and time limits; see [the output suite](AUDIO_OUTPUT.md). Neither path proves physical
speaker, headphone or Bluetooth output, published builds, or every device or pack.
Test pass/fail remains available separately and is never rewritten to hide a failure.

All tones derive from the existing 0.3-second, 44.1 kHz, half-scale 440 Hz PCM WAV.
The OGG fixture is stereo (the other fixtures are mono), generated using:

```sh
ffmpeg -i tone.wav -af 'pan=stereo|c0=c0|c1=c0' -c:a vorbis -strict experimental -b:a 128k tone.ogg
```

Both channels are sourced from the same mono tone. Decoder assertions check the channel
count, frame duration, sample rate, peak amplitude and first-channel zero crossings.
There is no shared corpus input; the report records that explicitly instead of inventing
a corpus hash. This suite adds evidence for existing app behavior and changes no app code.
