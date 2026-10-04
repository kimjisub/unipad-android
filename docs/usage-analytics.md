# Pack import and play usage tracking

Recovered implementation: commit `64a5ffc1`, copied byte for byte from all 31 changed/new files in the previously verified `unipad-1970714d` workspace. Both workspaces started at `20207d89`; there was no base-code divergence. The independent report dated 2026-09-30 12:40 UTC passed the archive write-target classification, 511 app tests, four design tests, and debug/release Kotlin compilation. That historical result does not verify the new first-input change or a device/server run.

## Events and privacy

- `pack_import`: one outcome per attempt, with source and a fixed result/error category.
- `pack_load`: one result per play-screen visit. Success waits until sound loading finishes; failure and leaving during loading remain separate outcomes.
- `play_start`: one first accepted pad press per visit, with `trigger=pad` or `trigger=autoplay`. Existing meaning and duration origin remain unchanged.
- `play_first_input`: one first human pad press per ready visit, with only `trigger=pad`. It is independent of `play_start`, so auto play cannot consume the human-input record. Screen and MIDI presses use the same human path. Step practice and guide presses count; choosing a mode, releases, invalid coordinates, inputs before readiness, and callbacks after leaving do not.
- `play_end`: one exit after playback started, with an elapsed-time bucket. Background/foreground changes and activity reconstruction retain the same ViewModel and visit.

No new identifier or parameter is introduced. Filenames, pack titles, creators, search terms, URLs, coordinates, raw errors, and exact durations are not event parameters. The first-input event proves an accepted input, not audible sound or physical LED output.

## Changes beyond the recovered implementation

Only the tracker/event vocabulary and four analytics/play-usage test files change. The tracker independently remembers whether human input has been recorded, settles both flags under its existing lock, and reports outside that lock. Duplicate presses return before constructing events. The import classification and original activity/runner call sites are preserved.

Regression checks reproduce auto play followed by human input at both the tracker and ViewModel boundaries. Existing checks cover readiness, invalid coordinates, mode-only changes, step/guide presses, reconstruction, cancellation, repeated starts/exits, and a failing analytics sink. A concurrent-input check ensures one first-input record; the routing check also includes this event in Firebase delivery expectations.

## Delivery and remaining checks

Debug events stay in local logs by default. Release events use the existing Firebase sink; a mocked SDK call or a `usage-firebase` line is not proof of server receipt. Device review must check import, human-first, auto-only, auto-then-human, step/guide input, reconstruction, and exit using the harness device lease. Keep device/log evidence separate from server-received evidence.

The first-input correction does not establish a completed-visit conversion rate: received data, visit matching, test exclusion, and the reporting period still need independent confirmation. No real-use zero is inferred from missing Android event rows. Production inclusion is tracked by JIS-35, and read-only aggregation by JIS-43.

Historical limitations preserved for review include indistinguishable directory creation failures (possibly permission, name limits, or disk space), message-dependent zip4j classification, and a plain ENOSPC FileNotFoundException classified as file access. The prior report also records import callback/cancellation and share-code reconstruction limitations; these were not silently fixed as part of first-input recovery.
