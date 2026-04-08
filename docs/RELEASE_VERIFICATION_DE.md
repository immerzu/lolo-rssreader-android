# Release-Verifikation

Stand dieser Verifikation:
- Version `1.87.06`
- VersionCode `136`
- Git-Basis `6785b14a1bc7a9ee34baf99795c1289a6bd0bd59`
- Status `lokaler Offline-Arbeitsstand, noch kein finaler Release-Tag`
- Datum `2026-04-08`
- Lokal verifiziert im aktuellen Projektordner:
  `D:\Codex\RSS_Reader_Android\rss_reader_full_project`

Ausgefuehrte Befehle:

```powershell
.\gradlew.bat --no-daemon assembleRelease
.\gradlew.bat --no-daemon testDebugUnitTest --tests "com.example.rssreader.data.network.FeedFetcherTest" --tests "com.example.rssreader.data.network.FeedParserTest" --tests "com.example.rssreader.data.repository.FeedRepositoryArticleContentTest" --tests "com.example.rssreader.data.repository.FeedRepositoryRefreshIntegrationTest" --tests "com.example.rssreader.data.repository.FeedRepositoryRefreshSupportTest" --tests "com.example.rssreader.data.db.ArticleFtsTriggerTest" --tests "com.example.rssreader.sync.BackgroundRefreshWorkerTest" --tests "com.example.rssreader.sync.RefreshSchedulerTest"
```

Ergebnis:
- `assembleRelease`: erfolgreich
- `testDebugUnitTest` fuer den relevanten Release-Block: erfolgreich

Logdateien:
- `D:\Codex\RSS_Reader_Android\Ausgabe_APK\release-verify-assembleRelease-v1.87.06-20260408-0634xx.log`
- `D:\Codex\RSS_Reader_Android\Ausgabe_APK\release-verify-tests-v1.87.06-20260408-0634xx.log`

Hinweis:
- Der zuletzt separat lokal verifizierte, sauber getaggte F-Droid-Stand bleibt `1.86.00 / 132`.
- Dieser Eintrag dokumentiert bewusst den aktuellen Offline-Arbeitsstand `1.87.06 / 136`.

Nicht durch diese Verifikation abgedeckt:
- Instrumentation-/UI-Tests auf Emulator oder Geraet
- Store-Einreichung, Signaturverteilung und externe Release-Prozesse
