# RSS Reader Android 1.87.30

## Kurzfassung
Dieses Release enthält vier interne Verbesserungen ohne sichtbare Funktionsänderung: Die Versionsnummer wird zentral aus `version.properties` gelesen, die WLAN-Erkennung ist zentralisiert, die Gradle-Abhängigkeiten liegen in einem Versionskatalog, und Debug-Builds werden mit demselben Release-Key signiert wie die Release-APK. Es enthält keine Änderungen an R8/Minify, Datenbankmigrationen oder am App-Verhalten.

## Änderungen

### Build & Tooling
- Versionsquelle konsolidiert: `version.properties` ist die einzige Quelle; `bumpReleaseVersion` schreibt nur noch `version.properties` (kein Regex-Edit am Build-Skript mehr).
- Gradle-Versionskatalog eingeführt: `gradle/libs.versions.toml` referenziert alle Versionen; Root- und App-Build nutzen `libs.*`.
- Debug-Signierung: `buildTypes.debug` nutzt denselben Release-`signingConfig` wie `release` (sofern `keystore.properties` vorhanden). Debug- und Release-APK sind damit gleich signiert — `adb install -r` aktualisiert eine installierte Version ohne Deinstallation.

### Code (Refactoring, Verhalten unverändert)
- WLAN-Erkennung zentralisiert: `data/network/ConnectivitySupport.kt` mit einer zentralen `hasWifiConnection`-Funktion; die drei bisherigen Duplikate (BackgroundRefreshWorker, RssReaderApp, DocumentImportExportSupport) wurden entfernt. Das beobachtbare Wifi-Gating-Verhalten bleibt unverändert.

## Technische Hinweise
- Version: 1.87.30
- Version Code: 159
- Keine R8-/ProGuard-/Minify-/ShrinkResources-Änderungen (dauerhaft deaktiviert).
- Keine Datenbankmigrationen.
- Keine Änderung am App-Verhalten.

## Validierung
- testDebugUnitTest
- assembleDebug
- assembleRelease

## Artefakte
- RSS-Reader-v1.87.30-release.apk (GitHub-Release-Asset, für den F-Droid-Reproduzierbarkeits-Check)

## Veröffentlichung und F-Droid-Status
- Tag: `v1.87.30` (annotiert)
- GitHub-Release: `RSS Reader 1.87.30` (mit Asset `RSS-Reader-v1.87.30-release.apk`)
- F-Droid: AutoUpdate aktiv (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags ^v[\d.]+$`). Der Tag `v1.87.30` löst den automatischen Build aus.
