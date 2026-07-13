# RSS Reader Android 1.87.27

## Kurzfassung
Dieses Release verbessert die automatische Aktualisierung beim App-Start und bei der Rückkehr in den Vordergrund sowie die konsistente Anzeige des Aktualisierungsstatus. Der Einzelfeed-Refresh ist in die globale Statusanzeige integriert und gegen parallele Aktualisierungen abgesichert. Die Unit-Tests wurden ergänzt und korrigiert, und veraltete lokale Tool-Pfade wurden korrigiert. Es enthält keine riskanten Änderungen an Build-Shrinking, Datenbankmigrationen oder der App-Architektur.

## Änderungen

### Aktualisierung
- Zuverlässigerer App-Start- und Vordergrund-Refresh über vereinheitlichte Logik (Cold-Start und Resume).
- Konsistente Refresh-Statusanzeige (Indikator und Statusmeldung) in der Hauptansicht.
- Einzelfeed-Refresh über das Feed-Menü in die globale Statusanzeige integriert.
- Schutz vor parallelen Aktualisierungen: laufende Refreshes sperren weitere manuelle und automatische Refreshes.

### Tests
- Unit-Test-Abdeckung für die Vordergrund-Refresh-Entscheidungslogik erweitert.
- Fehlerhafter Intervall-Test korrigiert (30-Minuten-Intervall erfordert entsprechende Hintergrundzeit).

### Werkzeuge
- Aktive lokale Tool-Pfade in `tools/*.ps1` und `tools/*.cmd` von `F:\Codex\RSS_Reader_Android` auf `F:\001_Coding_Projekte\RSS_Reader_Android` korrigiert.

### Dokumentation
- Release-Notizen für 1.87.27 ergänzt.

## Technische Hinweise
- Version: 1.87.27
- Version Code: 156
- Keine R8-/ProGuard-/Minify-/ShrinkResources-Änderungen.
- Keine Datenbankmigrationen.
- Keine riskanten Refactorings.
- Release ohne lokale keystore.properties erzeugt eine unsignierte APK.
- Release mit lokaler keystore.properties erzeugt eine signierte APK.

## Validierung
Folgende Aufgaben wurden erfolgreich ausgeführt:

- clean testDebugUnitTest
- assembleDebug
- assembleRelease

## Artefakte
- RSS-Reader-v1.87.27-debug.apk
- RSS-Reader-v1.87.27-release.apk

## Manuelle Hinweise
- Die Refresh-Szenarien (App-Start, Resume, Gesamt-Refresh, Einzelfeed-Refresh, Parallelität) wurden auf einem Gerät praktisch geprüft.
