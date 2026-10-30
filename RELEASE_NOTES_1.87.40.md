# RSS Reader Android 1.87.40

## Kurzfassung
Die WebView-Scrollbalken in der Artikelansicht sind jetzt ausgeblendet. Dadurch liegt am rechten Rand kein Scrollindikator mehr über dem Bereich für die Wischgeste zum Beitragswechsel. Das Scrollen des Beitrags per Finger bleibt unverändert. **Keine Datenbankmigration, keine neuen Abhängigkeiten und keine Änderung an Feeds oder gespeicherten Artikeln.**

## Änderungen

### Artikelansicht
- `ui/screens/ArticleReaderScreen.kt`: Der vertikale WebView-Scrollbalken wird deaktiviert.
- Der horizontale WebView-Scrollbalken wird ebenfalls deaktiviert.
- Die Änderung betrifft ausschließlich die Darstellung des Scrollindikators; der Inhalt bleibt weiterhin per Finger scrollbar.

## Technische Hinweise
- Version: 1.87.40
- Version Code: 169
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert), keine Datenbankmigrationen und keine neuen Abhängigkeiten.

## Validierung
- `testDebugUnitTest --rerun-tasks`: **365 Tests, 0 Fehler, 0 Errors, 1 übersprungen** in 27 Testsuiten.
- `lintDebug --rerun-tasks`: **0 Fehler, 26 Warnungen**.
- Gerätetest auf bq Aquaris X2 Pro: Debug-APK 1.87.40/169 per `adb install -r` datenerhaltend installiert; App-Start, Feedliste, Artikelliste und Reader erfolgreich. Ein langer Artikel ließ sich vertikal scrollen; vor und nach dem Scrollen war kein vertikaler oder horizontaler WebView-Scrollbalken sichtbar.
- Release-Build: signierte APK mit JDK-21-Toolchain und verpflichtendem `--rerun-tasks` erzeugt; `dexdump -d` auf `classes2.dex` bestätigt `HomeScreen`/`FeedConfigScreen` = **3298 / 790** (JDK-21-Spalte).
- APK: `RSS-Reader-v1.87.40-release.apk`, **13.015.893 Bytes**, SHA-256 `ede178b1cc03868a092b7add329926bf273138398f948cfed2e67d3840b30a45`, Signaturzertifikat `CN=RSS Reader` (SHA-256 `2fb2657d…`).
