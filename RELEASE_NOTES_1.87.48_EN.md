# RSS Reader Android 1.87.48

## Summary
Maintenance release: internal cleanup plus security and robustness improvements in the network and
import paths, and one visible fix in the reader. No new features, no schema or toolchain changes.

## Changes

### Reader
- Opening the full text duplicated the article title: once as the reader heading and again as the
  first bold paragraph of the fetched page. The identical heading is now removed. Different
  headings, subheadings (h2/h3) and all other content stay untouched.

### Security and privacy
- URL masking in debug logs now also covers percent-encoded parameter names (e.g. `to%6Ben`) and
  matches parameter names case-insensitively.
- Error messages for invalid feed URLs no longer contain the full URL, not even through the
  underlying cause.
- The shared HTTP configuration no longer carries credentials across HTTPS-to-HTTP redirects and
  does not follow SSL redirects.

### Stability and maintainability
- Charset detection, offline detection, relative time formatting, bounded import reading and the
  HTTP client each live in exactly one place, so feed fetching, full-text fetching and OPML import
  can no longer drift apart.
- Error handling for parallel feed refresh and OPML import is covered by additional tests
  (ordering, cancellation, error propagation).

## Technical notes
- Version: 1.87.48
- Version code: 177
- No schema change, no new dependency, no change to signing or toolchain.
- The local database format is unchanged; existing feeds, articles and favourites are preserved.

## Validation
- `testDebugUnitTest`: 448 tests, 0 failures, 0 errors, 1 skipped (46 suites).
- `lintDebug`: 0 errors, 24 warnings (unchanged).
- `assembleDebug`: successful.
- Device test on alice (Android 11): 28 of 28 checks passed (feeds, reader, search, favourites,
  OPML import/export, offline/reconnect, notifications, landscape).
