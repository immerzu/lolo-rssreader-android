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
Open with Android Studio and use this repository root as the project — the Gradle project *is* the repository
(in the maintainer's local working copy the same folder is named `rss_reader_full_project`).
Alternatively, `start-android-studio.ps1` launches Android Studio already configured with the local JDK/SDK paths.

## Build
Via Android Studio through `Build > Build APK(s)` or locally via `gradlew.bat assembleDebug`.
Before a release candidate, additionally run `gradlew.bat exportReleaseApk` (builds, signs and copies the APK
into `../Ausgabe_APK/`) and use the checklist in [RELEASE_CHECKLIST_RSS_READER_DE.md](RELEASE_CHECKLIST_RSS_READER_DE.md).

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
- Debug builds are signed with the SAME release key when `keystore.properties` is present,
  so `adb install -r` updates an installed release APK without uninstalling it.
  Without `keystore.properties`, debug builds fall back to the default debug keystore.
- Without `keystore.properties`, `assembleRelease` produces an unsigned APK (controlled, not an error).
  A signed release APK requires `keystore.properties` to be present locally.

**R8 / Minify:**
- `isMinifyEnabled` and `isShrinkResources` are **permanently forbidden** (never enable, never propose).
- Previous attempts to enable them caused severe problems.

## Archive (`_archiv`)

Documents and leftovers that are no longer part of the active project live in an `_archiv` folder:

- `_archiv/` (repository root) and `docs/archiv/`: retired one-off documents and duplicates
  (further release checklists, drafts, roadmap, git cheat sheet, GitHub Pages guides).
- `_archiv/` in the workspace root (`F:\001_Coding_Projekte\RSS_Reader_Android\_archiv\`): local leftovers
  (build logs, prompt files, `verify_tmp/`).

Everything under an `_archiv` folder is **not** part of the active project and is no longer maintained.
The two repository folders are listed in `.gitignore` and therefore not tracked; the files stay on disk
as a recovery reserve. Nothing there is deleted, and no new files are added without explicit approval.

## Gradle Configuration Note
The wrapper is already prepared for this machine and uses the locally stored Gradle 8.11.1 distribution.
The Kotlin toolchain is pinned to **JDK 21** (`kotlin { jvmToolchain(21) }` in `app/build.gradle.kts`);
without a discoverable JDK 21 the build fails with "No locally installed toolchains match". The Java
bytecode level stays at 17 (`jvmTarget`/`compileOptions`). Check with `gradlew.bat -q javaToolchains --no-daemon`.
