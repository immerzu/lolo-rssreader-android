# Release-Verifikation

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
