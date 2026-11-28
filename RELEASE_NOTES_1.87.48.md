# RSS Reader Android 1.87.48

## Kurzfassung
Wartungsrelease: interner Code aufgeräumt, Sicherheits- und Robustheitsverbesserungen im
Netzwerk- und Importpfad sowie eine sichtbare Korrektur im Reader. Keine neuen Funktionen,
keine Schema- oder Toolchain-Änderung.

## Änderungen

### Reader
- Beim Volltextabruf erschien der Artikeltitel doppelt: einmal als Überschrift des Readers und
  noch einmal als erster, fetter Absatz des geladenen Seiteninhalts. Die identische Titelzeile
  wird jetzt entfernt. Abweichende Überschriften, Zwischenüberschriften (h2/h3) und alle übrigen
  Inhalte bleiben unverändert.

### Sicherheit und Datenschutz
- Die Maskierung von URLs in Debug-Protokollen erkennt jetzt auch prozentkodierte Parameter
  (z. B. `to%6Ben`) und behandelt Parameternamen unabhängig von Groß- und Kleinschreibung.
- Fehlermeldungen bei ungültigen Feed-Adressen enthalten keine vollständige URL mehr, auch nicht
  über die zugrunde liegende Ursache.
- Die gemeinsame HTTP-Konfiguration verhindert das Mitführen von Anmeldedaten über
  HTTPS-zu-HTTP-Weiterleitungen; SSL-Weiterleitungen werden nicht mehr verfolgt.

### Stabilität und Wartbarkeit
- Zeichensatz-Erkennung, Offline-Erkennung, relative Zeitangaben, begrenztes Einlesen von
  Importdateien und der HTTP-Client liegen jeweils an genau einer Stelle; dadurch können
  Feed-Abruf, Volltextabruf und OPML-Import nicht mehr auseinanderlaufen.
- Fehlerbehandlung beim parallelen Feed-Abruf und beim OPML-Import ist durch zusätzliche Tests
  abgesichert (Reihenfolge, Abbruch, Fehlerweitergabe).

## Technische Hinweise
- Version: 1.87.48
- Version Code: 177
- Keine Schema-Änderung, keine neue Abhängigkeit, keine Änderung an Signierung oder Toolchain.
- Das Datenformat der lokalen Datenbank bleibt unverändert; vorhandene Feeds, Artikel und
  Favoriten bleiben erhalten.

## Validierung
- `testDebugUnitTest`: 448 Tests, 0 Fehler, 0 Errors, 1 übersprungen (46 Suiten).
- `lintDebug`: 0 Fehler, 24 Warnungen (unverändert).
- `assembleDebug`: erfolgreich.
- Gerätetest auf alice (Android 11): 28 von 28 Prüffällen bestanden (Feeds, Reader, Suche,
  Favoriten, OPML-Import/-Export, Offline/Reconnect, Benachrichtigungen, Querformat).
