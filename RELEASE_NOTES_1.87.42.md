# RSS Reader Android 1.87.42

## Kurzfassung
Favorisierte Artikel lassen sich jetzt an einer Stelle sammeln: Auf der Startseite erscheint ganz oben ein
Eintrag **„Favoriten"**, der alle gesternten Artikel aus allen Feeds enthält — neueste zuerst. Der Eintrag
ist nur sichtbar, solange mindestens ein Favorit existiert, verhält sich wie ein normaler Feed und
verschwindet automatisch, sobald der letzte Favorit entfernt wurde. **Keine Datenbankmigration, keine neuen
Abhängigkeiten, keine Änderung an echten Feeds.**

## Anlass
GitHub Issue **#13** („Starred articles hard to find"): Gesternte Artikel waren nur über den jeweiligen Feed
oder die Suche auffindbar, weil es keine Sammelansicht gab.

## Änderungen

### Favoriten-Sammlung auf der Startseite
- Neuer virtueller Eintrag „Favoriten" ganz oben in der Feed-Liste, im selben Zeilenformat wie echte Feeds
  (mit Ungelesen-Zähler). Er erscheint **nur**, wenn mindestens ein Artikel markiert ist.
- Öffnen führt in die **normale Artikelliste** mit allen Funktionen (öffnen, gelesen/ungelesen, Stern
  entfernen, teilen, „Alle als gelesen", Filter „Nur ungelesene").
- Kein Netz-Abruf, kein Feed-Menü (Bearbeiten/Löschen/Verschieben) — der Eintrag ist keine Quelle.
- Wird der letzte Favorit entfernt, bleibt die Ansicht offen und zeigt „Keine Favoriten"; der Eintrag
  verschwindet erst beim Zurückgehen zur Startseite.
- Der Eintrag wird beim Erscheinen vollständig angezeigt (Liste beginnt am Anfang, kein angeschnittener
  oberster Eintrag).

### Technisch
- `ArticleDao`: `observeFavorites`, `observeFavoriteCounts`, `markAllFavoritesRead`, `observeFavoritesNavigation`.
- `FeedRepository`: Sentinel `FAVORITES_FEED_ID = -1L`; die bestehenden Aufrufe (`observeArticles`,
  `observeFeed`, `markAllRead`, `refreshFeed`, `markFeedOpened`) arbeiten für diese ID auf `isFavorite = 1`.
- Keine Schema-Änderung — das Feld `isFavorite` existiert seit Migration 2.

## Technische Hinweise
- Version: 1.87.42
- Version Code: 171
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert), keine Datenbankmigrationen, keine neuen Abhängigkeiten.

## Validierung
- `testDebugUnitTest --rerun-tasks`: **373 Tests, 0 Fehler, 0 Errors, 1 übersprungen** in 28 Testsuiten;
  neu: `FavoritesFeedTest` mit 6 Tests (Sichtbarkeit bei 0/1 Favoriten, Sortierung, „alle als gelesen",
  Zähler, kein Netz-Refresh).
- Gerätetest **alice** (Redmi Note 9S, Android 11) und **bernd** (bq Aquaris X2 Pro, Android 10):
  ohne Favoriten kein Eintrag; nach dem Sternen Eintrag oben mit Zähler; Ansicht mit Titel/Artikel/Filter;
  „Alle als gelesen" setzt den Zähler auf 0; Artikel öffnen; letzten Stern entfernen → Ansicht bleibt,
  „Keine Favoriten"; zurück → Eintrag weg; Long-Press ohne Feed-Menü; Eintrag vollständig sichtbar.
