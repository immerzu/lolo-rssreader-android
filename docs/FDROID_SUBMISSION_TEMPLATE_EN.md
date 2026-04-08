# F-Droid Submission Template

Use this text as the basis for a new app submission in the official F-Droid GitLab.

## Recommended note before submission

The final release tag and GitHub release already exist for this version.

Current release:

`1.87.06 / 136`

## Suggested submission text

Title:

`New app submission: RSS Reader`

Body:

```text
Please consider adding RSS Reader to the official F-Droid repository.

App name: RSS Reader
Application ID: de.lolo.rssreader
License: Apache-2.0
Source code: https://github.com/immerzu/lolo-rssreader-android
Issue tracker: https://github.com/immerzu/lolo-rssreader-android/issues
Current release tag: v1.87.06

Short description:
Compact RSS reader with OPML, favorites, offline reading, and dark mode.

Main features:
- Subscribe to RSS and Atom feeds
- Import and export OPML
- Mark articles as read or unread
- Protect favorites from deletion
- Light and dark theme
- Background refresh with notifications
- Compact reader view with images and swipe navigation

Build:
- Gradle-based Android project
- Standard release build from repository root
- Command: ./gradlew assembleRelease
- Current release version: 1.87.06
- Current release versionCode: 136
- Final release tag: v1.87.06

Metadata:
- Fastlane metadata is included in the upstream repository under:
  fastlane/metadata/android/
- Prepared fdroiddata-style metadata draft is available under:
  docs/fdroid/de.lolo.rssreader.yml

```

## Submission links

Official docs:

* https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
* https://f-droid.org/en/docs/Inclusion_How-To/

GitLab locations:

* https://gitlab.com/fdroid/rfp/issues
* https://gitlab.com/fdroid/fdroiddata
