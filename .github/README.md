# Pull request unit tests

`unit-tests.yml` runs every app and design debug unit test on each pull request.
It has read-only repository access, uses a deliberately invalid Firebase fixture,
and needs no release keystore or GitHub secrets. Test reports are retained for
seven days, including on failure. It does not build a signed release, upload an
app, run device tests, or change required branch checks.

The LED press race tests keep both sets of 3,000 trials and all 200 inputs per
trial. Both the final LED state and main-thread delivery assertions remain.
Previously their barrier and worker join could wait forever, and an exception
on the tick worker was not reported to JUnit. Each now has a three-minute test
deadline, five-second barrier/join limits, and explicit worker failure reporting.
The worker is always asked to stop, including if a press or drain fails.

On the current baseline, the reported hang did not reproduce: the two unchanged
race tests passed in 26.6 seconds and the full suite finished in 80 seconds on
the maintenance Mac. These results do not establish the cause of the earlier
interruption. The deadlines make a future stalled run fail instead of waiting
indefinitely; the workflow also caps the test step at 12 minutes and the job at
20 minutes. The repetition counts are not reduced and no tests are excluded.

Release signing remains required: without `keystore.properties`, debug tests
can configure, but the release signing configuration has no key and release
signing validation fails. Existing release keys are loaded only when that local
file exists; CI never creates one.
