# Release dependency security check

`Release dependency security` runs for every pull request, each push to `main`,
every Monday at 03:23 UTC, and manual dispatch. It is a separate job from the
existing build, lint and unit test jobs. Leave it out of required branch checks
until its findings have been reviewed over an initial two-week observation period.

Run the same check locally with the repository's Android SDK and JDK installed:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_check_release_dependencies.py' -v
./gradlew :app:exportReleaseDependencies --init-script scripts/export_release_dependencies.gradle --no-daemon --console=plain
python3 scripts/check_release_dependencies.py
```

The Gradle init script reads `:app:releaseRuntimeClasspath` after resolution, so
it includes transitive Maven modules and the versions actually selected by Gradle,
including dependencies of local project modules. It does not parse the indented
human-readable dependency report or change app dependencies or release settings.
An unresolved or empty graph fails the export.

The Python check queries the [OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/)
using Maven package names and exact resolved versions. It follows per-package
pagination and rejects incomplete responses. Exit status is 0 for no known
matches, 1 for vulnerability matches, and 2 for an unavailable or malformed check.
Transient service failures have three bounded attempts; they cannot become a clean
result. There are no suppressed advisories.

`build/security/release-dependencies.json` and `build/security/osv-report.json`
are uploaded even when the check fails and retained for 14 days. A vulnerability
result includes the affected module, version and OSV advisory link. An error
result means the security status is unknown and needs a successful rerun after
the service or graph resolution problem is addressed.

Coverage is limited to known OSV records for the resolved Maven graph. It does
not discover unpublished vulnerabilities, inspect app code, or audit native
source code and local JARs without Maven coordinates. No release signing key or
service credential is needed; CI uses the existing test-only Firebase configuration.
