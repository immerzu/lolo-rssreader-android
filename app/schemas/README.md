# Room-Datenbank-Schemas

## Verzeichnisse

| Verzeichnis | Herkunft |
|---|---|
| `com.example.rssreader.data.db.AppDatabase/` | Frühere Paketstruktur (com.example.rssreader) — **existiert nicht mehr** (weder getrackt noch im Arbeitsbaum; geprüft 11.10.2026) |
| `de.lolo.rssreader.data.db.AppDatabase/` | Aktuelle Paketstruktur (de.lolo.rssreader) — 4 Schema-JSONs |

## Wichtig

Die `com.example`-Schemas stammten aus einer früheren Paketstruktur und sind **nicht mehr im Repo
vorhanden** (0 Treffer, geprüft 11.10.2026). Sie werden von keinem Test und keinem Pfad mehr
referenziert; `app/src/test` arbeitet ausschließlich mit `de.lolo.rssreader`. Falls sie je gebraucht
werden, liegen sie in der Git-Historie — der frühere Hinweis „nicht löschen" ist damit gegenstandslos.

Room verwendet diese JSON-Dateien bei der Schema-Validierung, um zu prüfen,
ob die aktuelle Datenbankstruktur mit den definierten Migrationen kompatibel ist.
