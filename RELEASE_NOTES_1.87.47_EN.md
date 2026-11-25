# RSS Reader Android 1.87.47

## Summary
Audit completion: 34 of 35 findings are fixed, one finding is not applicable. The main user-facing
changes are more efficient article lists and more robust handling of database read errors.

## Changes

### Article lists
- The list loads only the fields it displays (without the full HTML body) and fetches further
  articles in pages of 100 when scrolling to the end.
- Sorting and the “unread only” filter now run in the database; counters come from dedicated
  queries. Memory usage drops noticeably for very large feeds.
- Swipe navigation, favourites, read state and returning from the reader are unchanged; after
  reading, the list stays at the same position.

### Stability
- A temporary database read error is retried up to twice automatically.
- If the error persists, a message with “OK” is shown instead of a misleading empty screen; the
  next time the screen opens, the data is read again.

### Verified limits (no behaviour change)
- Very large articles are still truncated at 1,000,000 characters. Processing was measured on
  Android 8.0 (API 26) and Android 16 (API 36) with five runs each: stable, without crashes, ANR
  or out-of-memory errors.

## Technical notes
- Version: 1.87.47
- Version code: 176
- No schema change, no new dependency, no change to signing or toolchain.

## Validation
- `testDebugUnitTest`: 424 tests, 0 failures, 1 skipped (41 suites).
- `lintDebug`: 0 errors, 24 warnings.
- Instrumented UI tests: 8/8 on bernd (paging, rotation, feed switch, reader return).
- Device test on alice (debug build): confirmed by the user.
- Audit status: `docs/AUDIT_2026-11-13_BEFUNDE.md` (34 fixed, 1 not applicable).
