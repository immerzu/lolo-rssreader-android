# RSS Reader Android 1.87.46

## Kurzfassung
Fehlerbehebung am Hauptmenü der Startseite: Alle zwölf Einträge sind wieder vollständig
sichtbar, das Menü hat leicht abgerundete Ecken.

## Änderungen

### Hauptmenü
- Die starre Höhenbegrenzung von 320 dp ist entfernt. Die Menühöhe richtet sich jetzt nach dem
  Inhalt und dem tatsächlich verfügbaren Bildschirmbereich; Statusleiste, Navigationsleiste und
  Displayausschnitte werden berücksichtigt.
- Bei kleinen Displays, im Querformat oder bei vergrößerter Schrift bleibt das Menü scrollbar;
  alle Einträge sind erreichbar.
- Das Menüfenster hat einen dezenten Eckenradius von 6 dp. Breite, Position, Reihenfolge,
  Schriftgrößen und alle Funktionen bleiben unverändert.

## Technische Hinweise
- Version: 1.87.46
- Version Code: 175
- Keine Schema-Änderung, keine neue Abhängigkeit, keine Änderung an Signierung oder Toolchain.

## Validierung
- `testDebugUnitTest`: 408 Tests, 0 Fehler, 1 übersprungen (37 Suiten).
- `lintDebug`: 0 Fehler, 24 Warnungen.
- Gerätetest auf alice: Menü mit allen zwölf Einträgen, vier abgerundete Ecken.
