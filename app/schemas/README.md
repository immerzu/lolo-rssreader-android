# Room-Datenbank-Schemas

## Verzeichnisse

| Verzeichnis | Herkunft |
|---|---|
| `com.example.rssreader.data.db.AppDatabase/` | Frühere Paketstruktur (com.example.rssreader) |
| `de.lolo.rssreader.data.db.AppDatabase/` | Aktuelle Paketstruktur (de.lolo.rssreader) |

## Wichtig

Die `com.example`-Schemas stammen aus einer früheren Paketstruktur und bleiben
für Migrationshistorie und -validierung erhalten.

**Nicht löschen**, solange Migrationstests oder historische Datenbankpfade
davon abhängen könnten.

Room verwendet diese JSON-Dateien bei der Schema-Validierung, um zu prüfen,
ob die aktuelle Datenbankstruktur mit den definierten Migrationen kompatibel ist.
