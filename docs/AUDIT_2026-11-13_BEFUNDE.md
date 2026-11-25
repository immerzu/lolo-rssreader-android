# Audit-Befunde 2026-11-13 — Abarbeitungsstand

Quelle: Softwareaudit RSS Reader Android, Prüfstand 2026-11-13, Version 1.87.43 (172),
Commit `5e6ca96`. Abarbeitung am 2026-11-13, Branch `main`, **nicht committet**.

Teststand nach der Abarbeitung: `:app:testDebugUnitTest` **400 Tests, 0 Failures,
0 Errors, 1 übersprungen (34 Suiten)**.

**Nachtrag 2026-11-25 (RSS-AUDIT-002):** A16, A32 und A34 sind abgeschlossen, A29 bleibt mit
dokumentierter Restmaßnahme offen. Neuer Teststand: **414 Tests, 0 Failures, 0 Errors,
1 übersprungen (40 Suiten)**, Lint **0 Fehler / 24 Warnungen**. Nachweise je Befund unten
in der Statustabelle; Commits `5418025` (A34), `d8e382e` (A32), `3be3e3f` (A29), `919495f` (A16).

## Status je Befund

| ID | Schwere | Status | Umsetzung |
|---|---|---|---|
| RSS-A01 | Hoch | behoben | Sanitizer: `</script >`/`</script foo>`/`<script …/>`, `srcdoc`, `javascript:`/`vbscript:`-URLs; JavaScript bleibt aktiv (Webseiten-Funktion) |
| RSS-A02 | Hoch | behoben | `refreshFeedInternal` prüft in der Transaktion die Quelle, verwirft verspätete Antworten; Schreiben nur über `updateFetchResult` (Abrufspalten) |
| RSS-A03 | Hoch | behoben | Beim Quellenwechsel bleiben Favoriten erhalten und werden auf `legacy-<id>` umgeschlüsselt |
| RSS-A04 | Hoch | behoben | Ersatzschlüssel enthält immer den Titel, explizite Feldgrenzen |
| RSS-A05 | Hoch | behoben | `backup_build.ps1`: kein Auto-Löschen (`-Prune`), kein Überschreiben, versionsbewusste Sortierung |
| RSS-A06 | Mittel | behoben | FTS-Synchronisierung auch für heavy-Feeds |
| RSS-A07 | Mittel | behoben | Konfliktschlüssel in Blöcken à 500 (unter dem 999-Limit) |
| RSS-A08 | Mittel | behoben | Ohne Bilder werden `<img>`/`<picture>` entfernt statt nur `display:none` |
| RSS-A09 | Mittel | behoben | `xml:base` auf `<feed>` und `<link>`, relative URLs gegen die finale Response-URL (nach Redirects) |
| RSS-A10 | Mittel | behoben | `FeedEncoding`: BOM/Header/Prolog, `charset=` case-insensitiv; Import nutzt dieselbe Dekodierung |
| RSS-A11 | Niedrig | behoben | `published`/`updated` getrennt, feste Priorität |
| RSS-A12 | Mittel | behoben | `skipped` im Refresh-Ausgang; übersprungen ≠ erfolgreich |
| RSS-A13 | Mittel | behoben | 304 ohne Validator = Fehler; Refresh ohne Artikelbestand = kein Erfolg; Casts abgesichert |
| RSS-A14 | Mittel | behoben | `updateFeed` ruft nur bei URL-Wechsel das Netz; lokale Metadaten via `updateLocalMetadata` |
| RSS-A15 | Mittel | behoben | `Call.executeCancellable()` — Coroutine-Abbruch beendet den HTTP-Aufruf |
| RSS-A16 | Mittel | **behoben (25.11.2026)** | `ArticleListItem`-Projektion ohne `contentHtml`; Sortierung und Ungelesen-Filter in SQL; Seiten über `LIMIT` + Nachladen am Listenende; Zähler über eigene COUNT-Abfragen. Bewusst kein Fenster-Paging, damit Live-Aktualisierungen erhalten bleiben |
| RSS-A17 | Mittel | behoben | `loading` nicht mehr gespeichert; wiederhergestellter Entwurf wird nicht überschrieben |
| RSS-A18 | Mittel | behoben | `busy`/Import-Status nicht mehr gespeichert; Status-Timer wird beim Abschluss entwertet |
| RSS-A19 | Mittel | behoben | Volltext geht auch in die native Ersatzansicht |
| RSS-A20 | Mittel | behoben | Reader-Route trägt `favorites`-Kontext; Nachbarabfrage nutzt die Favoritenliste |
| RSS-A21 | Mittel | behoben | Route-ID initialisiert nur die erste Reader-Sitzung |
| RSS-A22 | Mittel | behoben | ARIA bleibt erhalten, Bild-`contentDescription`, Vor-/Zurück-Aktionen; `lang` = Gerätesprache (Näherung) |
| RSS-A23 | Niedrig | behoben | Favoritenzeile nicht verschiebbar, Pfeile an echten Nachbarn ausgerichtet |
| RSS-A24 | Mittel | behoben | Zentrale `UrlRedaction` (Userinfo, Token-Query, Fragment) |
| RSS-A25 | Mittel | behoben | Snapshot: hart kodierte Passwörter brechen ab, Ausgabe erst nach Prüfung, Versionskatalog enthalten |
| RSS-A26 | Mittel | behoben | jsoup `1.18.1` → `1.23.2` |
| RSS-A27 | Mittel | behoben | CI-Actions auf Commit-SHAs gepinnt |
| RSS-A28 | Niedrig | behoben | Doku an Policy angepasst (nur System-CAs) |
| RSS-A29 | Niedrig | **offen (Restmaßnahme dokumentiert)** | Link und Pages am 25.11.2026 erneut geprüft (404 / kein Host); keine weitere Verwendungsstelle im Baum; keine Ersatz-URL erfunden. Benötigt: Privacy-Repo/Pages anlegen, Store-Konsole prüfen (`docs/PrivacyLink für GooglePlay.txt`) |
| RSS-A30 | Mittel | behoben | OPML-`EntityResolver` liefert leere Quelle |
| RSS-A31 | Mittel | behoben | `data_extraction_rules`: alle Domänen ausgeschlossen |
| RSS-A32 | Mittel | **behoben (25.11.2026; Messung offen)** | Absolute Obergrenze 1 Mio. Zeichen pro Artikel, unabhängig vom Defensivmodus; Grenzwerttests ober-/unterhalb der Grenze (`FeedParserContentLimitTest`). **Messung auf API 26/36 weiterhin offen:** kein freigegebenes Testgerät mit diesen API-Ständen (alice 30, bernd 29, ingo neuer), Emulator-Tests ohne Freigabe unzulässig |
| RSS-A33 | Mittel | behoben | SAF-Stream öffnen/schließen auf `Dispatchers.IO` |
| RSS-A34 | Mittel | **behoben (25.11.2026)** | Zentrale Hülle `guardDatabaseRead`: Cancellation wird weitergereicht, andere Lesefehler werden protokolliert und gemeldet; abgesichert sind Feed-Übersicht, Favoriten, Artikelliste, Reader-Navigation und Suche. `RssReaderApp` zeigt eine sichtbare Meldung statt Leerzustand (`DataReadSafetyTest`) |
| RSS-A35 | Niedrig | behoben | Formular scrollbar; Menü nutzt `statusBarsPadding` und ist in der Höhe begrenzt |

## Geänderte Dateien (Auszug)

Produktion: `FeedFetcher.kt`, `FeedParser.kt`, `FeedEncoding.kt` (neu), `UrlRedaction.kt` (neu),
`OpmlCodec.kt`, `FeedRepository.kt`, `FeedRepositoryRefreshSupport.kt`, `FeedDao.kt`, `ArticleDao.kt`,
`ArticleReaderScreen.kt`, `ArticleListScreen.kt`, `FeedListScreen.kt`, `FeedConfigScreen.kt`,
`HomeScreen.kt`, `DocumentImportExportSupport.kt`, `RssReaderApp.kt`, `Screen.kt`,
`res/xml/network_security_config.xml`, `res/xml/data_extraction_rules.xml`,
`res/values*/strings.xml`, `gradle/libs.versions.toml`, `.github/workflows/ci.yml`,
`tools/backup_build.ps1`, `tools/export_source_snapshot.ps1`.

Tests: `FeedWriteSafetyTest` (neu), `FeedEncodingTest` (neu), `FeedParserAtomDateTest` (neu),
`FeedParserUniqueKeyTest` (neu), `UrlRedactionTest` (neu), `FeedFetcherTest`, `FeedParserTest`,
`OpmlCodecTest`, `FeedRepositoryRefreshSupportTest`, `ArticleReaderHtmlSecurityTest`,
`ArticleReaderScreenBaseUrlTest`.

## Bewusst abweichende Entscheidungen

- **RSS-A01:** JavaScript bleibt aktiv — die Artikelansicht braucht es für eingebettete Inhalte.
  Die Lücke wird im Sanitizer geschlossen; eine isolierte Origin wäre ein Umbau der Basis-URL.
- **RSS-A22:** `lang` ist die Gerätesprache, keine Artikelsprache (dafür fehlt ein Datenfeld).
- **RSS-A04:** Alte Fallback-Schlüssel ändern sich; Artikel ohne GUID/Link können beim ersten
  Refresh einmalig neu eingefügt werden (Lesestatus geht dabei verloren).
- **RSS-A16 (25.11.2026):** Paging über eine wachsende Seitengrenze (`LIMIT`), kein Fenster-Paging.
  Grund: Die Liste ist ein Live-Flow; ein verschiebbares Fenster würde bei neuen Artikeln oder
  Lesestatus-Änderungen Einträge springen lassen. Der HTML-Inhalt liegt nicht mehr in der Liste.
- **RSS-A34 (25.11.2026):** Nach einem Lesefehler endet der betroffene Flow (kein Endlos-Retry bei
  dauerhaft defekter Datenbank). Die UI behält Ladezustand bzw. letzten Stand und zeigt eine
  Meldung; der Nutzer bestätigt sie.
- **RSS-A29 (25.11.2026):** Keine Ersatz-URL eingetragen, solange keine gültige Datenschutzerklärung
  existiert. Die Restmaßnahme steht in `docs/PrivacyLink für GooglePlay.txt`.
- **RSS-A32 (25.11.2026):** Grenzwerte automatisiert getestet; die geforderte Messung auf API 26/36
  ist ohne freigegebenes Gerät/Emulator nicht möglich und bleibt offen.

