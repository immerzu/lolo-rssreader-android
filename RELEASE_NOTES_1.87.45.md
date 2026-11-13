# RSS Reader Android 1.87.45

## Kurzfassung
Sicherheits- und Stabilitätsrelease: 34 Befunde aus dem Softwareaudit vom 13.11.2026 sind behoben,
außerdem hat der Eintrag „Favoriten" auf der Startseite ein eigenes Stern-Icon.

## Änderungen

### Reader und Sicherheit
- Feed-HTML kann kein JavaScript mehr einschleusen: `</script >`, `<script …/>`, `<iframe srcdoc>`
  und `javascript:`/`vbscript:`-URLs werden entfernt. JavaScript bleibt für die Seitendarstellung aktiv.
- Bei ausgeschalteten Bildern werden `<img>`/`<picture>` entfernt statt nur per CSS ausgeblendet.
- Barrierefreiheit: ARIA-Attribute bleiben erhalten, Artikelbilder haben eine Beschreibung, der
  Artikelwechsel ist zusätzlich über eine Vor-/Zurück-Aktion erreichbar.
- Volltext erscheint jetzt auch in der nativen Ersatzansicht.

### Daten und Abruf
- Eine verspätete Refresh-Antwort kann keine inzwischen geänderte Feed-URL oder WLAN-Regel mehr
  zurücksetzen; Abrufresultate schreiben nur noch Abrufspalten.
- Beim Quellenwechsel bleiben Favoriten mit Inhalt und Link erhalten (eigener Schlüssel `legacy-<id>`).
- Ersatzschlüssel für Artikel ohne GUID/Link enthalten immer den Titel (keine Kollisionen mehr).
- Volltextindex wird auch für große („heavy") Feeds gepflegt; Konfliktabfragen laufen in Blöcken
  (kein „too many SQL variables" mehr).
- Name und WLAN-Regel sind offline änderbar; 304 ohne Validator und ohne Artikelbestand gilt nicht
  mehr als Erfolg; übersprungene Feeds zählen nicht mehr als aktualisiert.
- `xml:base` und relative Links werden korrekt aufgelöst; UTF-16/Latin-1-Feeds werden richtig dekodiert.

### Stabilität und Bedienung
- Rotation im Reader bleibt beim gelesenen Artikel; Favoriten-Wischen bleibt in der Favoritenliste.
- Feed-Formular verliert keinen Entwurf mehr und ist scrollbar; Startseite bleibt nach Abbruch
  nicht gesperrt; Menü ist auch im Querformat vollständig erreichbar.
- Abbruch einer Coroutine beendet den HTTP-Aufruf; Import/Export laufen vollständig im IO-Scope.

### Build und Doku
- jsoup 1.18.1 → 1.23.2 (zwei Advisories), CI-Actions auf Commit-SHAs gepinnt.
- Backup-Skript löscht keine Sicherungen mehr automatisch; Quelltext-Export bricht bei hart codierten
  Passwörtern ab und verliert keine vorhandene Ausgabe.

## Technische Hinweise
- Version: 1.87.45
- Version Code: 174
- Keine Schema-Änderung, keine neue Abhängigkeit außer dem jsoup-Update.
- Audit-Details und Status je Befund: `docs/AUDIT_2026-11-13_BEFUNDE.md`.

## Validierung
- `testDebugUnitTest --rerun-tasks`: siehe Testbericht des Release-Laufs.
- Gerätetest auf `alice`: Startseite, Artikelliste, Reader, Wischen, Rotation im Reader,
  Favoritenliste, Menü im Querformat.
