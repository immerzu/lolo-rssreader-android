# Release-Verifikation

Stand dieser Verifikation:
- Version `1.85.02`
- VersionCode `130`
- Tag `v1.85.02`
- Datum `2026-03-28`

Ausgefuehrte Befehle:

```powershell
.\gradlew.bat --no-daemon assembleRelease
.\gradlew.bat --no-daemon testDebugUnitTest --tests "com.example.rssreader.data.network.FeedFetcherTest" --tests "com.example.rssreader.data.network.FeedParserTest" --tests "com.example.rssreader.data.repository.FeedRepositoryArticleContentTest" --tests "com.example.rssreader.data.repository.FeedRepositoryRefreshIntegrationTest" --tests "com.example.rssreader.data.repository.FeedRepositoryRefreshSupportTest" --tests "com.example.rssreader.data.db.ArticleFtsTriggerTest" --tests "com.example.rssreader.sync.BackgroundRefreshWorkerTest" --tests "com.example.rssreader.sync.RefreshSchedulerTest"
```

Ergebnis:
- `assembleRelease`: erfolgreich
- `testDebugUnitTest` fuer den relevanten Release-Block: erfolgreich

Logdateien:
- `D:\Codex\RSS_Reader_Android\Ausgabe_APK\release-verify-assembleRelease-20260328-153136.log`
- `D:\Codex\RSS_Reader_Android\Ausgabe_APK\release-verify-tests-20260328-153224.log`

Gepruefte Zusatzpunkte im aktuellen Source:
- keine erneut eingefuehrten Mojibake-Strings in `app/src/main/java`
- Charset-/Parser-Tests erwarten weiterhin korrekten semantischen Text
- Release-Hinweis mit `assembleRelease` steht in `README.md` und `RELEASE_CHECKLIST_RSS_READER_DE.md`

Nicht durch diese Verifikation abgedeckt:
- Instrumentation-/UI-Tests auf Emulator oder Geraet
- Store-Einreichung, Signaturverteilung und externe Release-Prozesse
