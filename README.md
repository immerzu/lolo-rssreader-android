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

`keystore.properties` contains only non-secret values (`storeFile`, `keyAlias`).
The signing passwords are NOT stored in plain text anywhere in the project.
They are read at build time from the OS-bound credential store (Windows Credential Manager)
via `tools/get-signing-secret.ps1` (targets: `rssreader_store_password`, `rssreader_key_password`).

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Fill in `storeFile` and `keyAlias`.
3. Make sure the two credential-manager entries `rssreader_store_password` and `rssreader_key_password` exist.
   If they still exist as plain text from an old setup, migrate them once with `tools/migrate-signing-secrets.ps1`.
4. Make sure `.gitignore` excludes the file.

**Important:**
- `keystore.properties` must not be versioned (it points at signing configuration).
- The signing passwords live only in the OS credential store, never in repository files.
- The release key (`*.jks`, e.g. `signing/rss-reader-release.jks`) must be managed outside the repository and backed up separately.
- Debug builds require NO `keystore.properties` and work without signing configuration.
- Without `keystore.properties`, `assembleRelease` produces an unsigned APK (controlled, not an error).
  A signed release APK requires `keystore.properties` to be present locally.

**R8 / Minify:**
- `isMinifyEnabled` and `isShrinkResources` are **permanently forbidden** (never enable, never propose).
- Previous attempts to enable them caused severe problems.

## Gradle Configuration Note
The wrapper is already prepared for this machine and uses the locally stored Gradle 8.11.1 distribution.
