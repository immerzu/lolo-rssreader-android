# Audit-Befunde 2026-11-13 — Abarbeitungsstand

Quelle: Softwareaudit RSS Reader Android, Prüfstand 2026-11-13, Version 1.87.43 (172),
Commit `5e6ca96`. Abarbeitung am 2026-11-13, Branch `main`, **nicht committet**.

Teststand nach der Abarbeitung: `:app:testDebugUnitTest` **400 Tests, 0 Failures,
0 Errors, 1 übersprungen (34 Suiten)**.

**Nachtrag 2026-11-25 (RSS-AUDIT-002):** A16, A32 und A34 sind abgeschlossen, A29 bleibt mit
dokumentierter Restmaßnahme offen. Neuer Teststand: **414 Tests, 0 Failures, 0 Errors,
1 übersprungen (40 Suiten)**, Lint **0 Fehler / 24 Warnungen**. Nachweise je Befund unten
in der Statustabelle; Commits `5418025` (A34), `d8e382e` (A32), `3be3e3f` (A29), `919495f` (A16).

**Nachtrag 2026-11-25 (RSS-AUDIT-003, Regressionsprüfung):**
- **A34-Wiederherstellung behoben:** Nach einem vorübergehenden Fehler endete der Lesefluss
  dauerhaft (kein erneuter Leseversuch). Jetzt zwei Wiederholungen mit 500 ms, danach geordnetes
  Ende; dauerhafter Fehler bleibt begrenzt (3 Versuche). Commit `791833e`.
- **A16-Randfälle abgesichert:** leere Liste, Filter an + alles gelesen, letzter ungelesener Artikel
  wird gelesen, Seitengrenze 100/101, neuer Artikel während Refresh (keine Duplikate, verdrängter
  Artikel kehrt beim Nachladen zurück). Commit `dd04b8a`.
- **A32 ist jetzt ausdrücklich „nicht vollständig abgeschlossen"**: Grenzwerte sind getestet, die
  Leistungsmessung auf API 26/36 fehlt. A29 bleibt offen.
- **Testintegrität:** 408 → 414 (+3 A34, +2 A32, +6 A16, −5 In-Memory-Filtertests, die durch
  gleichwertige SQL-Fälle ersetzt wurden); durch AUDIT-003 weitere +7 (2 A34, 5 A16).
  Kein Test wurde ohne Ersatz entfernt.

**Nachtrag 2026-11-25 (RSS-AUDIT-004, UI-Regressionsrisiken):**
- **Unit:** Wisch-Navigation kennt Artikel jenseits der geladenen Seite (150/100, Nachbar von
  Artikel 51 ist 50); Lesestatus und Favorit wirken auf nicht geladene Artikel.
- **Instrumentiert (`ArticleListPagingUiTest`):** Nachladen beim Scrollen ans Ende, State-Restoration
  (Rotation) erhält die Listenposition, Feedwechsel zeigt keine fremden Artikel. Der Paging-Test
  wurde per Mutation (Nachlade-Trigger deaktiviert) **rot** belegt.
- **Prüfblockade:** Auf alice lässt MIUI die Installation der Test-APK nicht zu
  (`INSTALL_FAILED_USER_RESTRICTED`); instrumentierte Tests laufen deshalb nur auf bernd (7/7 grün).
- **Ergebnis:** keine Regression reproduziert. Teststand **423 / 0 / 0 / 1** (40 Suiten), Lint 0/24.
  Commit `835e791`. A29/A32 bleiben offen.

**Nachtrag 2026-11-25 (RSS-AUDIT-005, Reader-Rückkehr und Git-Abgleich):**
- **Reader-Rückkehr getestet:** `ArticleListNavigationUiTest` bildet den echten Navigationspfad nach
  (Liste → Artikel → zurück). Nach dem Paging bleiben Listenposition, Filter und die nachgeladenen
  Artikel erhalten. Auf bernd grün; **Rotnachweis**: ohne `rememberSaveable` für `pageLimit` fehlen die
  Artikel nach der Rückkehr (Timeout). Commit `e22cd8b`.
- **alice – Prüfblockade belegt:** Instrumentierte Tests laufen dort **generell** nicht. Zwei Versuche
  (7 min und 150 s) und die Gegenprobe mit dem **bestehenden** `ArticleListUnreadFilterUiTest` (90 s)
  enden ohne Ergebnis; Logcat zeigt „Crash of app de.lolo.rssreader running instrumentation", der
  Crash-Buffer ist leer. Kein Bezug zu den AUDIT-004/005-Tests; auf bernd laufen alle 8 Tests.
- **Git-Abgleich:** `git rev-list --left-right --count origin/main...HEAD` → **`0 11`**. Es sind
  **11** Commits vor `origin/main`; die früheren Angaben „6" bzw. „7" waren ab einem Zwischenstand
  gezählt und damit falsch. Kein Commit fehlt, keiner liegt hinter `origin/main`.

**Nachtrag 2026-11-25 (RSS-AUDIT-006, A29/A32):**
- **A29 — Entwurf erstellt, Veröffentlichung offen:** Datenverarbeitung am Code geprüft (HTTPS-only,
  keine Analytics/Werbung/Crash-SDKs, Übertragung nur an die abonnierten Quellserver, lokale
  Speicherung in Room/DataStore, Logdatei nur in Debug-Builds, Backup deaktiviert). Sachlicher Entwurf
  in `docs/privacy/DATENSCHUTZ_RSS_READER_DE.md` und `…_EN.md` (als Entwurf gekennzeichnet, Kontakt
  und ggf. Postanschrift **[zu bestätigen]**). Bereitstellungsweg und konkrete Freigaben in
  `docs/PrivacyLink für GooglePlay.txt`. **Offen:** öffentliches Repo + GitHub Pages, Eintrag in der
  Play-Konsole.
- **A32 — JVM-Messung durchgeführt:** `FeedParserPerformanceTest` misst 900k, 1.000k und 1.100k Zeichen
  sowie einen Feed mit 5×900k:

  | Fall | Eingabe | Artikel | max. Inhalt | Dauer | Heap-Delta |
  |---|---|---|---|---|---|
  | 900k | 900.000 | 1 | 900.000 | 108 ms | ~11 MB |
  | 1.000k (Grenze) | 1.000.000 | 1 | 1.000.000 | 59 ms | ~9 MB |
  | 1.100k | 1.100.000 | 1 | **1.000.000 (gekürzt)** | 48 ms | ~16 MB |
  | 5×900k | 4.500.000 | 5 | 900.000 | 176 ms | ~52 MB |

  Bewertung: stabil, korrekte Kürzung, Verarbeitung unter 0,2 s (JVM); kein Mangel belegt. Die
  Messung ist **keine** API-26/36-Messung. **Freigabeanforderung:** Emulator mit System-Image API 26
  und API 36 (x86_64, google_apis) starten und denselben Test dort ausführen — nur mit ausdrücklicher
  Freigabe.

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
| RSS-A29 | Niedrig | **offen (Entwurf erstellt, AUDIT-006)** | Datenverarbeitung geprüft; Entwurf DE/EN unter `docs/privacy/` (als Entwurf markiert, Kontakt **[zu bestätigen]**); Bereitstellungsweg + Freigaben in `docs/PrivacyLink für GooglePlay.txt`. **Offen:** öffentliches Repo + GitHub Pages, Play-Konsole |
| RSS-A30 | Mittel | behoben | OPML-`EntityResolver` liefert leere Quelle |
| RSS-A31 | Mittel | behoben | `data_extraction_rules`: alle Domänen ausgeschlossen |
| RSS-A32 | Mittel | **teilweise (25.11.2026, AUDIT-006)** | Grenzwerte behoben und getestet; **JVM-Leistungsmessung** liegt vor (`FeedParserPerformanceTest`, 900k–1,1M Zeichen, 5×900k: 48–176 ms, Heap-Delta 9–52 MB, Kürzung korrekt). **API-26/36-Messung fehlt weiterhin** — kein freigegebenes Gerät, Emulator-Freigabe offen. Befund daher nicht abgeschlossen |
| RSS-A33 | Mittel | behoben | SAF-Stream öffnen/schließen auf `Dispatchers.IO` |
| RSS-A34 | Mittel | **behoben (25.11.2026, AUDIT-003)** | Zentrale Hülle `guardDatabaseRead`: Cancellation wird weitergereicht, andere Lesefehler gemeldet, vorübergehende Fehler bis zu zweimal mit 500 ms Wartezeit wiederholt (danach geordnetes Ende). Abgesichert: Feed-Übersicht, Favoriten, Artikelliste, Reader-Navigation, Suche; sichtbare Meldung statt Leerzustand. Tests: Erholung nach transientem Fehler, begrenzte Wiederholung, Cancellation (`DataReadSafetyTest`) |
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

