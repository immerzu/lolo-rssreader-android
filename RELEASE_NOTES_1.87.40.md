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

## Veröffentlichung und F-Droid-Status

**Veröffentlicht am 2026-10-30** (Stand dieses Abschnitts: 2026-10-30):
- Annotierter Tag `v1.87.40` (Tag-Objekt `953f0c0c358671e9c323db68d2dd4c4cc3fe402c` → Commit `865952c575eb32885ec2c9d67a124d47c80b9142`), auf **beiden** Remotes (`origin` + `gitlab`).
- GitHub-Release „RSS Reader 1.87.40" (nicht Draft, nicht Prerelease, veröffentlicht 2026-10-30T07:38:53Z) mit Asset `RSS-Reader-v1.87.40-release.apk` (13.015.893 Bytes, `sha256:ede178b1cc03868a092b7add329926bf273138398f948cfed2e67d3840b30a45`); CI-Run zum Push `success`.
- JDK-Nachweis am veröffentlichten Artefakt (`dexdump -d` auf `classes2.dex`): `HomeScreen` = **3298**, `FeedConfigScreen` = **790** — JDK-21-Spalte.

**F-Droid: ausgeliefert** (Stand 2026-11-04):
- Bot-MR **!55040** („bot: Update RSS Reader to 169") am **2026-10-31T11:12:28Z** gemergt; fdroiddata führt `CurrentVersion: 1.87.40` / `169` (Bot-Commit `8e3d6909`).
- **Reproduzierbarkeits-Build am 2026-11-03** (02:32:37–02:35:04 UTC): `BUILD SUCCESSFUL in 2m 18s`, `Successfully built de.lolo.rssreader:169 from 865952c…`, **`compared built binary to supplied reference binary successfully`**, erlaubter Signer `2fb2657d…`, `1 build succeeded`.
- **Publish-Lauf 2026-11-03 23:47:34 – 2026-11-04 01:04:32 UTC.** Die F-Droid-Paketseite führt **„Version 1.87.40 (169) suggested — Added on Nov 04, 2026"**, der Index `suggestedVersionCode: 169` (Pakete 169, 168, 166). **Die Version ist damit öffentlich verfügbar.**
