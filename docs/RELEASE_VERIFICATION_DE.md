# Release-Verifikation (historisches Protokoll)

> **Historisch — Stand 2026-04-08, Version 1.87.06 / VersionCode 136.** Die unten genannten Pfade
> (`F:\Codex\...`), der Paketname `com.example.rssreader` und die Version sind der damalige Stand.
> Heute gilt: Paket `de.lolo.rssreader`, Projektordner
> `F:\001_Coding_Projekte\RSS_Reader_Android\rss_reader_full_project`, aktuelle Version siehe
> `version.properties` (derzeit 1.87.36 / 165). Nicht als aktuelles Prüfprotokoll verwenden —
> die heutige Nachweiskette beschreibt der Skill `fdroid-release-rssreader` (Ablauf D).

Stand dieser Verifikation:
- Version `1.87.06`
- VersionCode `136`
- Git-Basis `21be442a7bf9f4d4f9106104e0de5d566625f830`
- Status `finaler Release-Commit mit Git-Tag v1.87.06`
- Datum `2026-04-08`
- Lokal verifiziert im aktuellen Projektordner:
  `F:\Codex\RSS_Reader_Android\rss_reader_full_project`

Ausgefuehrte Befehle:

```powershell
.\gradlew.bat --no-daemon assembleRelease
.\gradlew.bat --no-daemon testDebugUnitTest --tests "com.example.rssreader.data.network.FeedFetcherTest" --tests "com.example.rssreader.data.network.FeedParserTest" --tests "com.example.rssreader.data.repository.FeedRepositoryArticleContentTest" --tests "com.example.rssreader.data.repository.FeedRepositoryRefreshIntegrationTest" --tests "com.example.rssreader.data.repository.FeedRepositoryRefreshSupportTest" --tests "com.example.rssreader.data.db.ArticleFtsTriggerTest" --tests "com.example.rssreader.sync.BackgroundRefreshWorkerTest" --tests "com.example.rssreader.sync.RefreshSchedulerTest"
```

Ergebnis:
- `assembleRelease`: erfolgreich
- `testDebugUnitTest` fuer den relevanten Release-Block: erfolgreich

Logdateien:
- `F:\Codex\RSS_Reader_Android\Ausgabe_APK\release-verify-assembleRelease-v1.87.06-20260408-065108.log`
- `F:\Codex\RSS_Reader_Android\Ausgabe_APK\release-verify-tests-v1.87.06-20260408-065108.log`

Nicht durch diese Verifikation abgedeckt:
- Instrumentation-/UI-Tests auf Emulator oder Geraet
- Store-Einreichung, Signaturverteilung und externe Release-Prozesse
