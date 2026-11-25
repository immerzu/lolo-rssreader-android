# RSS Reader Android 1.87.47

## Kurzfassung
Abschluss des Softwareaudits: 34 von 35 Befunden sind behoben, ein Befund ist nicht anwendbar.
Nutzerrelevant sind vor allem effizientere Artikellisten und ein stabilerer Umgang mit
Datenbank-Lesefehlern.

## Änderungen

### Artikellisten
- Die Liste lädt nur die dargestellten Felder (ohne den vollständigen HTML-Inhalt) und holt weitere
  Artikel in 100er-Seiten nach, sobald man ans Ende scrollt.
- Sortierung und Filter „nur ungelesen" laufen in der Datenbank; die Zähler stammen aus eigenen
  Abfragen. Bei sehr großen Feeds sinkt der Speicherverbrauch deutlich.
- Wisch-Navigation, Favoriten, Lesestatus und die Rückkehr aus dem Reader bleiben unverändert;
  nach dem Lesen steht die Liste weiterhin an derselben Stelle.

### Stabilität
- Ein vorübergehender Lesefehler der Datenbank wird bis zu zweimal automatisch wiederholt.
- Bleibt der Fehler bestehen, erscheint eine Meldung mit „OK" statt eines irreführenden leeren
  Bildschirms; beim nächsten Öffnen wird erneut gelesen.

### Geprüfte Grenzen (keine Verhaltensänderung)
- Sehr große Artikel werden weiterhin bei 1.000.000 Zeichen gekürzt. Die Verarbeitung wurde auf
  Android 8.0 (API 26) und Android 16 (API 36) mit je fünf Durchläufen gemessen: stabil, ohne
  Abstürze, ANR oder Speicherfehler.

## Technische Hinweise
- Version: 1.87.47
- Version Code: 176
- Keine Schema-Änderung, keine neue Abhängigkeit, keine Änderung an Signierung oder Toolchain.

## Validierung
- `testDebugUnitTest`: 424 Tests, 0 Fehler, 1 übersprungen (41 Suiten).
- `lintDebug`: 0 Fehler, 24 Warnungen.
- Instrumentierte UI-Tests: 8/8 auf bernd (Paging, Rotation, Feedwechsel, Reader-Rückkehr).
- Gerätetest auf alice (Debug-Build): vom Nutzer bestätigt.
- Auditstand: `docs/AUDIT_2026-11-13_BEFUNDE.md` (34 behoben, 1 nicht anwendbar).
