# UI test device preparation

Both `ci-connected-tests.sh` and `run-base-features.sh` record the borrowed device's
`system.font_scale`, `global.window_animation_scale`,
`global.transition_animation_scale` and `global.animator_duration_scale` before
building or starting tests. Values other than an absent setting or numeric `1`
are set to `1.0`. A failed read, write or confirmation stops the run.

Preparation installs the debug app and test APK and runs `DeviceSettingsProbe`
in the target app process. This is a separate instrumentation component, not a
JUnit test: it does not change test discovery, selection or suite counts. It
checks the app's resource font scale and the animation settings read by the app,
plus the process's animator scale on API 26 and later (the public getter is not
available on API 24–25). Only its explicit success verdict lets the test run start.
Gradle's existing animation-disable behavior during connected tests is unchanged.

Results default to `.ci-results` for connected tests and
`.ci-results/base-features` for the base feature suite. Set `CI_RESULTS_DIR` to a
persistent output folder to keep them elsewhere. Each invocation replaces the
previous invocation's files, so archive a round before reusing that folder.

- `device-settings.json`: timestamped `before`, normalized `after`, and `final`
  snapshots, the keys reset, app verification time, and any preparation/read error.
- `device-settings-app.txt`: raw app-process confirmation output.
- `ui-status.txt`: its first line says whether preparation completed, when it ran,
  and each reset's old/new values; the following lines retain exit code and elapsed
  time, plus the connected runner's existing test verdict.
- `gradle-ui.txt` and (for the base suite) `instrumentation.txt`: raw test output.
  Connected tests also retain `preflight-build.txt`, `logcat.txt` and
  `test-starts.txt`.

The exit handler records all four settings again after success or failure; it
does not rewrite them or conceal test failures. Termination signals use that same
handler. A forced kill or a disconnected device can prevent the final read; the
next invocation always records and prepares the device afresh. This preparation
belongs to the repository runners and does not change external device lending tools.
