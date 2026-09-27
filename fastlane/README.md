fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android build_only

```sh
[bundle exec] fastlane android build_only
```

Build a signed release AAB without uploading (build/sign verification).

### android deploy

```sh
[bundle exec] fastlane android deploy
```

Build a signed AAB and upload to the given Play track. Pass track:internal|alpha|beta|production.

### android promote

```sh
[bundle exec] fastlane android promote
```

Promote an already-uploaded build (e.g. from internal) into production as a staged release. Pass version_code:NNN and fraction:0.1 (1 = everyone).

### android rollout

```sh
[bundle exec] fastlane android rollout
```

Change the staged rollout fraction of the current production release. Pass fraction:0.5 (1 = complete) and version_code:NNN.

### android halt

```sh
[bundle exec] fastlane android halt
```

Halt the current staged production rollout (keeps the release, stops new users getting it).

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
