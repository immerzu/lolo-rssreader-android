# F-Droid Submission Template

Use this text as the basis for a new app submission in the official F-Droid GitLab.

## Recommended note before submission

The current release tag `v1.70.01-translate-release` exists in the source repository and is publicly reachable.

For an official submission, it is cleaner if the tagged release is also reachable from the default branch (`main`) or from a clearly named release branch.

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
Current release tag: v1.70.01-translate-release

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
- Optional article translation

Build:
- Gradle-based Android project
- Standard release build from repository root
- Command: ./gradlew assembleRelease

Metadata:
- Fastlane metadata is included in the upstream repository under:
  fastlane/metadata/android/

Important note about Anti-Features:
The optional translation feature in the 1.70.x line uses a Google web translation endpoint without an official API key.
Because of that, this feature may need the NonFreeNet anti-feature label.
The core RSS reading functionality does not depend on this translation feature.
```

## Submission links

Official docs:

* https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
* https://f-droid.org/en/docs/Inclusion_How-To/

GitLab locations:

* https://gitlab.com/fdroid/rfp/issues
* https://gitlab.com/fdroid/fdroiddata
