# UI test device preparation

Both `ci-connected-tests.sh` and `run-base-features.sh` record the borrowed device's
`system.font_scale`, `global.window_animation_scale`,
`global.transition_animation_scale` and `global.animator_duration_scale` before
building or starting tests. Values other than an absent setting or numeric `1`
are set to `1.0`. They also record the display size and density overrides `wm size`
and `wm density` report, and reset any override (`wm size reset`, `wm density reset`).
Tests that turn the display into a tablet or a larger screen put these back when
they end, but a run stopped inside one leaves them on the emulator: a later run then
starts on a 720 dp-wide display, where the navigation bar is the taskbar and the
navigation overlay wait never sees the phone bar height it expects. Navigation and
cutout overlays and the user rotation are not reset here; the tests that change them
restore what they found. A failed read, write or confirmation stops the run.

Preparation installs the debug app and test APK and runs `DeviceSettingsProbe`
in the target app process. This is a separate instrumentation component, not a
JUnit test: it does not change test discovery, selection or suite counts. It
checks the app's resource font scale and the three animation settings read by
the app. It does not check the process's internal animator scale: a process
without a window can report the default even when the system setting differs.
Only its explicit success verdict lets the test run start.
The connected runner then uninstalls both preparation APKs before Gradle starts
so tests receive a fresh install, and design-only runs leave neither APK behind.
Preparation failures also attempt to remove both APKs.
Gradle's existing animation-disable behavior during connected tests is unchanged.

Results default to `.ci-results` for connected tests and
`.ci-results/base-features` for the base feature suite. Set `CI_RESULTS_DIR` to a
persistent output folder to keep them elsewhere. Each invocation replaces the
previous invocation's files, so archive a round before reusing that folder.
Each invocation deletes `ui-status.txt` when it starts and writes it again only in
its exit handler: a folder without `ui-status.txt` holds a run that never finished
(killed, disconnected or still running), never a pass.

- `device-settings.json`: timestamped `before`, normalized `after`, and `final`
  snapshots (an empty `display.size` or `display.density` means no override), the keys reset, app verification time, and any preparation/read error.
- `device-settings-app.txt`: raw app-process confirmation output.
- `ui-status.txt`: its first line says whether preparation completed, when it ran,
  and each reset's old/new values; the following lines retain exit code and elapsed
  time, plus the connected runner's existing test verdict.
- `gradle-ui.txt` and (for the base suite) `instrumentation.txt`: raw test output.
  Connected tests also retain `preflight-build.txt`, `logcat.txt` and
  `test-starts.txt`.

The exit handler records all six values again after success or failure; it
does not rewrite them or conceal test failures. Termination signals use that same
handler. A forced kill or a disconnected device can prevent the final read; the
next invocation always records and prepares the device afresh. This preparation
belongs to the repository runners and does not change external device lending tools.
