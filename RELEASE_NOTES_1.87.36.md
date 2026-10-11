# RSS Reader Android 1.87.36

## Kurzfassung
Reines Build-Release ohne funktionale Änderungen: Die Kotlin-Toolchain ist auf JDK 21 festgelegt, damit der F-Droid-Reproducible-Build-Vergleich gegen die Release-APK besteht. Zusätzlich Versionsanhebung.

## Änderungen

### Build
- `app/build.gradle.kts`: `kotlin { jvmToolchain(21) }` ergänzt (mit erklärendem Kommentar).
- `version.properties`: `VERSION_NAME=1.87.36`, `VERSION_CODE=165`.
- `jvmTarget`/`compileOptions` bleiben auf Java 17 (Bytecode-Major 61) — nur das ausführende JDK ist fixiert.
- Kein `org.gradle.java.home` (absoluter Windows-Pfad wäre auf dem Buildserver ungültig).

### Hintergrund
F-Droid baut jede Version selbst mit JDK 21 und vergleicht byteweise gegen das Release-Asset. Release-APKs aus einem JDK-17-Build unterscheiden sich in `classes2.dex` (8 Methoden, u. a. `HomeScreenKt.HomeScreen`, `FeedConfigScreenKt.FeedConfigScreen`), wodurch der CI-Job `fdroid build` scheitert und die Version nicht veröffentlicht wird.

## Technische Hinweise
- Version: 1.87.36
- Version Code: 165
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert), keine Datenbankmigrationen, keine funktionalen Änderungen.

## Validierung
- testDebugUnitTest (335 Tests, 0 Fehler, 1 übersprungen)
- Kontrollbuild auf dem 1.87.35-Stand ergab bit-identisch den JDK-21-Build (`classes2.dex` `e3aa25c1…`)
- Methodenvergleich über 53.664 Methoden: 0 Abweichungen zum JDK-21-Muster

## Artefakte
- RSS-Reader-v1.87.36-release.apk (13.015.349 Bytes, SHA-256 `554c8b55…`)

## Veröffentlichung und F-Droid-Status
- Tag: `v1.87.36` (annotiert), an GitHub und GitLab gepusht.
- GitHub-Release: `RSS Reader 1.87.36` (Latest, nicht Draft, nicht Prerelease).
- F-Droid: **veröffentlicht** — manueller MR !49133 am 16.09.2026 von einem Maintainer gemergt, Repro-Build am 17.09.2026 erfolgreich, Index führt `suggestedVersionCode: 165`.
