English | [Deutsch](README_DE.md)

# RSS Reader

A simple Android project in Kotlin + Jetpack Compose.

## License
This project is licensed under the Apache License 2.0.
See [LICENSE](LICENSE) for details.

## Features
- Feed overview
- Add feed
- Load RSS/Atom
- Parser
- Room database
- Article list
- Reader view with text and images below
- Simple settings

## Getting Started
Open with Android Studio and use the `rss_reader_full_project` folder as the project.
Alternatively, `start-android-studio.ps1` launches Android Studio already configured with the local JDK/SDK paths.

## Build
Via Android Studio through `Build > Build APK(s)` or locally via `gradlew.bat assembleDebug`.
Before a release candidate, additionally run `gradlew.bat assembleRelease` and use the checklist in [RELEASE_CHECKLIST_RSS_READER_DE.md](RELEASE_CHECKLIST_RSS_READER_DE.md).

## F-Droid Metadata
Store metadata for F-Droid is located under `fastlane/metadata/android/`.
A short note on the official submission is in [docs/FDROID_EINREICHUNG_DE.md](docs/FDROID_EINREICHUNG_DE.md).
A prepared submission text is in [docs/FDROID_SUBMISSION_TEMPLATE_EN.md](docs/FDROID_SUBMISSION_TEMPLATE_EN.md).

## Release Signing (maintainers only)

Release builds require a local `keystore.properties`.
This file is NOT part of the repository and must never be committed.

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Replace the placeholders with real values.
3. Make sure `.gitignore` excludes the file.

**Important:**
- `keystore.properties` contains signing passwords and must not be versioned.
- The release key (`*.jks`) must be managed outside the repository.
- If `keystore.properties` has already been shared, its contents must be considered compromised.
- Debug builds require NO `keystore.properties` and work without signing configuration.
- Without `keystore.properties`, `assembleRelease` produces an unsigned APK (controlled, not an error).
  A signed release APK requires `keystore.properties` to be present locally.

**R8 / Minify:**
- `isMinifyEnabled` and `isShrinkResources` are intentionally kept disabled.
- Previous attempts to enable them caused severe problems.
- No change without a separate, explicit approval.

## Gradle Configuration Note
The wrapper is already prepared for this machine and uses the locally stored Gradle 8.7 distribution.
