# RSS Reader Android 1.87.17

## Kurzfassung
Dieses Release verbessert die Projektsicherheit, die Export-Sicherheit, die Behandlung unverschlüsselter HTTP-Feeds und die Testabdeckung. Es enthält keine riskanten Änderungen an Build-Shrinking, Datenbankmigrationen oder der App-Architektur.

## Änderungen

### Sicherheit
- Lokale Signing-Dateien werden vom Repository ausgeschlossen.
- Beispiel-Datei für lokale Signing-Konfiguration ergänzt.
- Export-Skript gegen versehentliches Einfügen von Secrets gehärtet.
- Getrackte APK-Artefakte aus dem Repository entfernt.
- Unverschlüsselte HTTP-Feeds werden klar und kontrolliert abgelehnt.

### Stabilität
- Zusätzliche Tests für HTML-Sanitizing im Artikel-Reader ergänzt.
- Zusätzliche Edge-Case-Tests für Feed-Parsing ergänzt.
- HTTP-only-Feed-Verhalten per Tests abgesichert.
- Alte Room-Schema-Historie dokumentiert.

### Dokumentation
- Signing-Verhalten dokumentiert.
- Verhalten ohne keystore.properties dokumentiert.
- Release-Checkliste ergänzt.
- Export-Sicherheitsregeln dokumentiert.
- R8/Minify bleibt bewusst deaktiviert und ist entsprechend dokumentiert.

## Technische Hinweise
- Version: 1.87.17
- Version Code: 146
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
- exportReleaseApk
- exportReleaseBundle

## Artefakte
- RSS-Reader-v1.87.17-debug-20260518-131204.apk
- RSS-Reader-v1.87.17-release.apk
- RSS-Reader-v1.87.17-release.aab

## Manuelle Hinweise
- Falls eine frühere keystore.properties extern geteilt wurde, sollte der Release-Key als kompromittiert betrachtet und ersetzt werden.
- Die HTTP-Warnung für http://-Feeds kann optional noch auf einem echten Gerät visuell geprüft werden.
