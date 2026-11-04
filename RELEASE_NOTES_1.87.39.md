# RSS Reader Android 1.87.39

## Kurzfassung
Reine Härtung von zwei Fehlerpfaden aus dem laufenden Betrieb: Die Hintergrundaktualisierung behandelt einen Coroutine-Abbruch jetzt korrekt (kein falscher „Fehler + Retry" mehr), und die Leseransicht kann bei einem Datenbankfehler beim Markieren als gelesen nicht mehr abstürzen. Zusätzlich gilt eine fehlgeschlagene Markierung nicht mehr als erledigt, sodass der nächste Auslöser sie erneut versucht. **Keine neuen Funktionen, keine Änderungen an Feeds, Filtern, Navigation oder Oberfläche, keine Datenbankmigration.**

## Änderungen

### M1 – Coroutine-Cancellation im Hintergrund-Worker
- `sync/BackgroundRefreshWorker.kt`: `CancellationException` wird im Refresh-Fehlerpfad **und** im Benachrichtigungs-Fehlerpfad unverändert weitergereicht, statt protokolliert und in `Result.retry()` umgewandelt zu werden. Normale Netzwerk-/Feed-Fehler behalten Retry und Logging unverändert.
- Gleiches Muster wie im bereits vorhandenen Schutz in `data/repository/FeedRepositoryRefreshSupport.kt` (`RefreshCoordinator`) und `FeedRepository.importOpmlEntry`.

### M2 – Gelesen-Markierung im Reader
- `ui/screens/ArticleReaderScreen.kt`: neue interne Hilfsfunktion `markArticleReadOnce(articleId, alreadyMarkedIds, markRead, onFailure)`.
  - Der Guard `markedReadArticleIds` wird **erst nach erfolgreichem Schreiben** ergänzt; ein fehlgeschlagener Versuch blockiert keinen erneuten Versuch.
  - Gewöhnliche Schreibfehler werden über `DebugLogger.w` protokolliert und nicht geworfen — ein DB-Fehler kann die Leseransicht nicht mehr abstürzen lassen.
  - `CancellationException` wird weitergeworfen; fatale JVM-`Error` werden nicht als Schreibfehler verschluckt (`catch (e: Exception)`).
- Verwendet an beiden Produktionspfaden: Artikel öffnen (`LaunchedEffect`) und Weiterwischen (`triggerSwipe`). Erfolgreiche Markierungen und der Doppelmarkierungsschutz bleiben unverändert.

## Technische Hinweise
- Version: 1.87.39
- Version Code: 168
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert), keine Datenbankmigrationen, keine neuen Abhängigkeiten, keine Änderungen an CI-Workflows.

## Validierung
- testDebugUnitTest (365 Tests, 0 Fehler, 1 übersprungen; neu: 5 Regressionstests für Cancellation im Refresh- und Benachrichtigungspfad sowie für Guard-, Fehler- und Cancellation-Verhalten von `markArticleReadOnce`)
- lintDebug (0 Fehler, 24 Warnungen)
- Gerätetest auf bq Aquaris X2 Pro (Android 10 / API 29) mit dem Debug-Build 1.87.39/168: Artikel öffnen, Weiterwischen, Leseransicht verlassen, App in den Hintergrund, Ungelesen-Filter — jeweils korrekte Gelesen-Markierung, keine Regression beobachtet. Die erzwungene Cancellation unter einem Android-Systemstopp ist nicht gerätegeprüft; sie ist durch die Unit-Tests abgesichert.
- Release-Build: signierte APK mit JDK-21-Toolchain erzeugt; JDK-Nachweis am Artefakt (`dexdump -d` auf `classes2.dex`, `insns size` von `HomeScreen`/`FeedConfigScreen` = **3298 / 790** = JDK-21-Spalte).
- APK: `RSS-Reader-v1.87.39-release.apk`, 13.015.893 B, SHA-256 `304559d2ecde3558b851f05873cbdab49b9d647ff3242bde84439cade17cc95a`, Signaturzertifikat `CN=RSS Reader` (SHA-256 `2fb2657d…`).

## Veröffentlichung und F-Droid-Status

**Veröffentlicht am 2026-10-17** (Stand dieses Abschnitts: 2026-10-30):
- Annotierter Tag `v1.87.39` (Tag-Objekt `789be3e585dc49d6f5f8f99ea5a87c959c871e0f` → Commit `4a5c6498d099aa01077b74ef0f73a5c3917609d1`), auf **beiden** Remotes.
- GitHub-Release „RSS Reader 1.87.39" (nicht Draft, nicht Prerelease, veröffentlicht 2026-10-17T11:36:53Z) mit Asset `RSS-Reader-v1.87.39-release.apk` (13.015.893 Bytes, `sha256:304559d2ecde3558b851f05873cbdab49b9d647ff3242bde84439cade17cc95a`).

**F-Droid: ausgeliefert** (belegt am 2026-10-30):
- Bot-MR **!53039** („bot: Update RSS Reader to 168"), gemergt am **2026-10-18T09:37:28Z**, CI-Pipeline `success`; der Bot-Commit `299cdb0e` trägt `CurrentVersion: 1.87.39` / `CurrentVersionCode: 168`.
- Build `de.lolo.rssreader:168` am **2026-10-20** (Log 03:33:24–03:35:41): `BUILD SUCCESSFUL`, `compared built binary to supplied reference binary successfully`, erlaubter Signer `2fb2657d…`, `1 build succeeded`.
- Der F-Droid-Index führt `suggestedVersionCode: **168**` (Pakete 1.87.39/168, 1.87.37/166, 1.87.36/165). **1.87.38/167 fehlt** dort — siehe `RELEASE_NOTES_1.87.38.md`.
