# Design: WordPress-Kommentar-Historie nachladen

Stand: 2026-11-04 · Status: Vorschlag zur Freigabe · Autor: Agent (mit Nutzerfreigabe zum Umfang)

## 1. Warum

Der RSS-Kommentarfeed einer WordPress-Site liefert nur die **letzten 10 Kommentare** und ist nicht
blätterbar (`/comments/feed/?paged=2` → 404; `?page=2` liefert identisch dieselben 10). Bei aktiven
Blogs rotiert ein Kommentar binnen Stunden heraus und ist im Reader nicht mehr abrufbar, obwohl er
auf der Website weiter existiert.

Belegt am 2026-11-04 an `propagandamelder-reloaded.de`: RSS-Feed 10 Items (03.11. 22:38 – 04.11. 12:42),
der gesuchte Kommentar (id 296935, 03.11. 10:43) fehlte; die WordPress-REST-API lieferte ihn
(`/wp-json/wp/v2/comments?per_page=100`), inklusive Post-Titel über `_embed=up`.

## 2. Ziel

Kommentare, die im RSS-Kommentarfeed nicht (mehr) erscheinen, aus der WordPress-REST-API derselben Site
nachladen und als normale Artikel des Feeds speichern — ohne Änderung der Listen-, Reader- oder
Suchoberfläche.

## 3. Nicht-Ziele

- Keine generische Feed-Paginierung (`?paged=`) für beliebige Feeds.
- Keine Unterstützung anderer CMS (Ghost, Blogger, Discourse …).
- Kein neuer Screen, keine neue Abhängigkeit (JSON über `org.json`).
- Keine Änderung von Reader-HTML/-CSS (der bestehende Normalizer greift).
- Keine Rückwirkung auf Feeds ohne Aktivierung.
- **Kein Aufräumen** bereits geladener `wp-comment-*`-Artikel, wenn das Feld wieder geleert wird
  (sie bleiben in der Liste; löschbar über die vorhandene Funktion „Feed-Einträge löschen").
- **Kein Re-Check** nachträglich editierter oder auf der Website gelöschter Kommentare — der
  `after`-Cursor holt Altes nicht erneut.

## 4. Umfang (Nutzerentscheidung 2026-11-04)

**Nur Kommentar-Historien.** Die Funktion ist pro Feed optional und standardmäßig aus.

## 5. Datenmodell (Room, Migration 11 → 12)

`feed_entity` erhält zwei nullable Spalten:

| Spalte | Typ | Bedeutung |
|---|---|---|
| `commentsApiUrl` | TEXT NULL | REST-Endpunkt, z. B. `https://site/wp-json/wp/v2/comments`; NULL/leer = Funktion aus |
| `lastCommentsSyncAt` | INTEGER NULL | Zeitpunkt (Millis) des letzten erfolgreichen Kommentar-Abrufs; steuert `after` |

- Migration `MIGRATION_11_12` mit zwei `ALTER TABLE … ADD COLUMN`.
- Schema-Export `app/schemas/12.json`; `@Database(version = 12)`.
- Artikel bleiben unverändert: `uniqueKey = "wp-comment-<id>"` verhindert Duplikate.

## 6. Erkennung und Konfiguration

- Im Feed-Editor (`FeedConfigScreen`) ein optionales Feld **„Kommentar-Historie (WordPress-API)"**.
- Vorschlagswert automatisch, wenn die Feed-URL auf `/comments/feed` endet und `siteUrl` bekannt ist:
  `<schema>://<host>/wp-json/wp/v2/comments`. Der Nutzer kann den Wert überschreiben oder leeren.
- Validierung: nur `https` (wie Feed-URLs), sonst Fehlermeldung wie bei ungültiger Feed-URL.

## 7. Abruf

Neue Klasse `data/network/WpCommentsFetcher.kt` (OkHttp wie `FeedFetcher`, `AppUserAgent`, Timeouts
wie beim Feed-Abruf):

```
GET <commentsApiUrl>?per_page=100&order=desc&_embed=up[&after=<ISO8601>][&page=<n>]
```

- `after` = ISO-8601 aus `lastCommentsSyncAt` (UTC, ohne Millisekunden).
- **Erstlauf** ist definiert als `lastCommentsSyncAt IS NULL` (Feld nie erfolgreich gesetzt).
- Seiten: `page=1..N`; Abbruch bei leerer Antwort, weniger als `per_page` Einträgen oder Erreichen von N.
- **N = 5** pro regulärem Lauf (max. 500 Kommentare).
- **Erstlauf (Backfill):** N = 10 (max. 1000 Kommentare), danach `lastCommentsSyncAt` setzen.
- HTTP 404/401/Timeout → Abbruch ohne Retry, Log über `DebugLogger`, Feed-Refresh bleibt erfolgreich.

## 8. Parsing und Mapping

Neue Klasse `data/network/WpCommentsParser.kt`:

- Eingabe: JSON-Array; Felder `id`, `date`, `link`, `author_name`, `content.rendered`,
  `_embedded.up[0].title.rendered` (Fallback: ohne Post-Titel).
- Ausgabe: `ParsedComment(id, postTitle, authorName, publishedAtMillis, link, contentHtml)`.

Mapping auf `ArticleEntity`:

| Feld | Quelle |
|---|---|
| `uniqueKey` | `wp-comment-<id>` |
| `title` | `Kommentar zu <postTitle> von <authorName>` (Fallback: `Kommentar von <authorName>`) |
| `link` | `link` (Kommentar-Anker) |
| `publishedAt` | `date` (ISO-8601 → Millis) |
| `contentHtml` | `content.rendered` (durchläuft den bestehenden Normalizer beim Anzeigen) |
| `plainText`, `imageUrls` | über die vorhandenen Helfer aus `FeedParser` (`htmlToPlainText`, `extractImageUrls`) — dafür werden beide von `private` auf `internal` gehoben (kein Verhaltenswechsel, bestehende Tests decken sie ab) |

## 9. Integration in den Refresh

Der Kommentar-Abruf ist ein **eigener Schritt**, der **nicht** von den Early-Returns des Feed-Abrufs
abhängt (Heavy-Throttle, HTTP 304, ETag-FastPath 200 mit gleichem ETag). Er läuft deshalb im Aufrufer
(`refreshFeed` / `refreshFeedsBounded`) unmittelbar nach `refreshFeedInternal` — sonst bliebe der
Backfill bei einem unveränderten Feed aus und `lastCommentsSyncAt` bliebe NULL.

1. Nur wenn `commentsApiUrl` gesetzt.
2. Kommentare abrufen (Abschnitt 7).
3. Einfügen mit `uniqueKey = "wp-comment-<id>"` **und** Dedupe gegen bereits vorhandene Artikel des
   Feeds über den `link`: RSS-Kommentar-Items nutzen `guid` bzw. `link` als Schlüssel und kollidieren
   nie mit `wp-comment-<id>`; derselbe Kommentar läge sonst doppelt in der Liste.
4. **Backfill (Erstlauf):** `isRead = true`, zählt **nicht** in `newArticles`/`RefreshRunStats` und
   löst keine Benachrichtigung aus. **Inkrementelle Läufe** verhalten sich wie normale Artikel
   (ungelesen, zählen).
5. `lastCommentsSyncAt = now` nur bei erfolgreichem Abruf; Fehler werden gefangen und geloggt, der
   Feed-Refresh gilt trotzdem als erfolgreich.

## 10. Tests (JUnit 4 + Robolectric, wie bestehend)

| Test | Inhalt |
|---|---|
| `WpCommentsParserTest` | Fixture mit 2 Kommentaren inkl. `_embedded.up`; Titel-/Datums-/Link-Mapping; fehlender Post-Titel |
| `WpCommentsFetcherTest` | MockWebServer: `after`-Parameter korrekt, Seitenabbruch bei < 100 Einträgen, 404 → leeres Ergebnis ohne Exception |
| `FeedRepository` | zweiter Lauf erzeugt keine Duplikate (`uniqueKey`), `lastCommentsSyncAt` wird nur bei Erfolg gesetzt |
| Migration | `MigrationTestHelper` 11 → 12: Spalten vorhanden, Bestandsdaten unverändert |

## 11. Aufwand und Risiken

- Aufwand: **4–6 h** inkl. Tests und Migration.
- Risiken: REST-API nicht überall aktiv (dann bleibt der Feed unverändert); Kommentar-HTML fremdbestimmt
  (Normalizer greift, keine neue Angriffsfläche); Rate-Limits durch `after` + N ≤ 10 geschont.

## 12. Meine Entscheidungen (vom Nutzer korrigierbar)

1. **Backfill-Tiefe:** 10 Seiten / 1000 Kommentare beim ersten Lauf — genug für „Historien", ohne die
   Site zu belasten. (Alternative: unbegrenzt bis nichts mehr kommt.)
2. **Titelform** wie im RSS-Feed („Kommentar zu … von …"), damit gemischte Listen einheitlich aussehen.
3. **Konfiguration pro Feed** statt global — die API gilt nur für WordPress-Sites.
4. **Kein automatischer Backfill bei bestehenden Installationen**, erst nach Aktivierung am Feed.
