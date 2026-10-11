# RSS Reader Android 1.87.37

## Kurzfassung
Fehlerbehebung: Nach „Alle Feeds als gelesen markieren" und „Alle gelesenen Einträge löschen" blieben Feeds mit unverändertem ETag/Last-Modified beim anschließenden Aktualisieren ohne Einträge, weil der Abruf übersprungen wurde. Zusätzlich setzt „Aktualisierungsdatum zurücksetzen" für einen einzelnen Feed jetzt den vollständigen Abrufzustand zurück und markiert dessen Artikel als ungelesen.

## Änderungen

### Fehlende Artikel nach dem Löschen gelesener Einträge
- Ursache: Die Skip-Pfade (HTTP 304 sowie 200 mit identischem ETag) griffen auch dann, wenn der Feed lokal keinen Artikelbestand mehr hatte; der Feed-Datensatz behielt dabei `etag`/`lastModified`.
- `data/repository/FeedRepository.kt` (`refreshFeedInternal`): Die Skip-Pfade werden deaktiviert, solange ein Feed lokal keinen Artikelbestand hat (`hasLocalArticles`); auch das Heavy-Feed-Throttling greift in diesem Fall nicht.
- `data/repository/FeedRepository.kt` (`deleteAllReadEntries`): Beim Löschen gelesener Einträge wird der bedingte Abrufzustand der betroffenen Feeds zurückgesetzt (Log `refresh_state_reset scope=feedsMitGelesenenEintraegen`, `FeedDao.resetConditionalStateForFeedsWithReadArticles`).
- `data/repository/FeedRepository.kt` (`markAllReadGlobally`): protokolliert jetzt die Zahl der betroffenen Artikel (Log `mark_all_read beendet: betroffene_artikel=<n>`).
- `data/repository/FeedRepository.kt` (`resetFeedUpdatedAt`, Einzel-Feed): nullt `etag`, `lastModified` und `lastFetchedAt` und setzt alle vorhandenen Artikel des Feeds auf `isRead=false` (Log `reset_fetch_state … artikelAufUngelesen=<n>`). Der globale Reset bleibt unverändert; `deleteAllReadEntries` und die Skip-Pfad-Logik wurden nicht anderweitig angefasst.

## Technische Hinweise
- Version: 1.87.37
- Version Code: 166
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert), keine Datenbankmigrationen.

## Validierung
- testDebugUnitTest (347 Tests, 0 Fehler, 1 übersprungen; neue Tests für Skip-Pfade, Lösch-Reset und Einzel-Feed-Reset)
- assembleDebug, lintDebug (0 Fehler, 24 Warnungen)
- Gerätetest auf vivo V2405A (Android 16, Debug-Build): nach „als gelesen markieren" + „gelesene löschen" nahmen alle 8 Feeds den regulären Abrufpfad (224 neue Einträge); Dauerhaftigkeitslauf über vier Refresh-Zyklen inklusive App-Neustart; Einzel-Feed-Reset im Log belegt (Nutzer bestätigt: Einträge erschienen ungelesen).

## Artefakte
- RSS-Reader-v1.87.37-release.apk (13.015.349 Bytes, SHA-256 `d9aa46df…`)

## Veröffentlichung und F-Droid-Status
- Tag: `v1.87.37` (annotiert), an GitHub und GitLab gepusht.
- GitHub-Release: `RSS Reader 1.87.37` (nicht Draft, nicht Prerelease).
- F-Droid: kein manueller Merge-Request. Die Metadaten führen `AutoUpdateMode: Version` und `UpdateCheckMode: Tags ^v[\d.]+$`; erwartet wird, dass der `checkupdates`-Lauf die Version selbstständig nachzieht. Stand der Beobachtung (11.10.2026): noch kein Bot-Merge-Request vorhanden. Veröffentlichter Stand zum Zeitpunkt des Releases: 1.87.36 / 165.
