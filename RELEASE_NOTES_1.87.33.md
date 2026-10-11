# RSS Reader Android 1.87.33

## Kurzfassung
Aufräum-Release: Die Einstellung „Beim Scrollen als gelesen markieren" wurde vollständig entfernt, das Markieren beim Durchlaufen der Liste bleibt fest aktiv. Zusätzlich wurde `gradlew` (Unix) an die Windows-Konfiguration angeglichen.

## Änderungen

### Einstellung entfernt
- `data/settings/AppPreferences.kt`: Feld `markReadOnScroll` gelöscht.
- `data/settings/SettingsRepository.kt`: Lesen, Setter `setMarkReadOnScroll` und der DataStore-Schlüssel `mark_read_on_scroll` gelöscht.
- `ui/screens/SettingsScreen.kt`: Karte „Lesen" mit dem Schalter gelöscht.
- `res/values{,-de,-en}/strings.xml`: `settings_section_reading`, `settings_mark_read_on_scroll_title`, `settings_mark_read_on_scroll_subtitle` gelöscht.
- Verhalten unverändert: Markieren beim Durchlaufen der Liste ist fest aktiv (ausgesetzt nur beim Filter „nur ungelesen"); Markieren beim Öffnen, Dialogaktion „Als gelesen" und „Alle als gelesen" bleiben.

### Build
- `gradlew` (Unix-Shell): `-Djava.net.preferIPv4Stack=false` ergänzt — konsistent zu `gradlew.bat` und `gradle.properties`.

## Technische Hinweise
- Version: 1.87.33
- Version Code: 162
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert).
- Keine Datenbankmigrationen.
- Ein verwaister DataStore-Schlüssel `mark_read_on_scroll` kann in Bestandsinstallationen wirkungslos liegen bleiben.

## Validierung
- testDebugUnitTest (333 Tests, 0 Fehler, 1 übersprungen)
- assembleDebug

## Artefakte
- RSS-Reader-v1.87.33-release.apk (13.015.349 Bytes, SHA-256 `3e63bfaa…`)

## Veröffentlichung und F-Droid-Status
- Tag: `v1.87.33` (annotiert), an GitHub und GitLab gepusht.
- GitHub-Release: `RSS Reader 1.87.33` (nicht Draft, nicht Prerelease).
- F-Droid: **nicht veröffentlicht** — die Release-APK stammte aus einem JDK-17-Build, der Repro-Build-Vergleich des Bot-MR !48494 scheiterte an `classes2.dex`.
