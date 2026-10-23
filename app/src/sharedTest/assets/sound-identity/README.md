# Archive sound identity observations

The corpus is pinned to the public unipad.io commit
`69881749822aace4d2af9ebe9b67659421fc8f48` (269 cases, SHA-256 recorded in
`corpus.sha256`). It is a separate observation input; the previous shared corpus
and its historical results remain unchanged.

Only the ten `soundIdentity` cases run here: KS-G601 through KS-G604 and
KS-G620 through KS-G625. Both test layers create the original ZIP directly from
the fixture, use `UniPackImporter`, then load the imported pack with
`UniPackFolder` and drive `SoundRunner`.

`SoundIdentityProbe` reads original entry names and content hashes from the ZIP.
It matches the file the runner actually decodes by filesystem identity and the
bytes read at decode time. The resulting queue records `archiveEntry`, `sha256`,
`loop`, and `wormhole`. Stored paths, preservation of every original entry's
contents, and runner play requests are separate diagnostics. Ambiguous or missing
identity traces are unverified; when identity is traced, content loss is a failure
even when the selected queue otherwise matches. Storage-only normalization changes do not change an
entry's identity. Expectations are available only to the final comparison.

Host observations are opt-in:

```sh
SOUND_IDENTITY_OUT=/path/to/host-results.json \
SOUND_IDENTITY_APP_COMMIT=$(git rev-parse HEAD) \
./gradlew :app:testDebugUnitTest \
  --tests '*SoundIdentityObservationTest' --tests '*SoundIdentityProbeTest' \
  --rerun --no-daemon --console=plain
```

The host substitutes decoding and records selection only. Device test
`SoundIdentityObservationDeviceTest` uses the native decoder, records internal
and app-specific external storage separately, and writes
`files/sound-identity-results.json` in the debug app's data directory. Pass the
`appCommit` instrumentation argument to record the app baseline, and
`internalFileSystemType` / `externalFileSystemType` for filesystem types measured
by `adb shell stat -f` (the app sandbox denies `Files.getFileStore`). The device
also logs each case and the complete report in numbered `SoundIdentityData`
chunks. Preserve the test logcat and reconstruct these chunks before the next
round: Gradle removes the installed apps and their files after tests. Neither layer
opens an audio stream, so neither establishes speaker output.

An observation test's success means the harness finished and wrote results.
Read each case's `status`; a successful test task does not mean the ten cases
passed. The device test records the corpus hash and filesystem properties, and
the host does the same. Each raw round must be preserved before another test
round overwrites Gradle results.
