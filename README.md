# RSS Reader

Ein einfaches Android-Projekt in Kotlin + Jetpack Compose.

## Lizenz
Dieses Projekt steht unter der Apache License 2.0.
Details siehe [LICENSE](LICENSE).

## Enthalten
- Feed-Uebersicht
- Feed hinzufuegen
- RSS/Atom laden
- Parser
- Room-Datenbank
- Artikel-Liste
- Leseransicht mit Text und Bildern darunter
- einfache Einstellungen

## Projektstart
Mit Android Studio oeffnen und den Ordner `rss_reader_full_project` als Projekt verwenden.
Alternativ startet `start-android-studio.ps1` Android Studio bereits mit den lokalen JDK-/SDK-Pfaden.

## Build
Per Android Studio ueber `Build > Build APK(s)` oder lokal ueber `gradlew.bat assembleDebug`.
Vor einem Release-Kandidaten zusaetzlich `gradlew.bat assembleRelease` ausfuehren und die Checkliste in [RELEASE_CHECKLIST_RSS_READER_DE.md](F:/Codex/RSS_Reader_Android/rss_reader_full_project/RELEASE_CHECKLIST_RSS_READER_DE.md) verwenden.

## F-Droid Metadaten
Store-Metadaten fuer F-Droid liegen unter `fastlane/metadata/android/`.
Eine kurze Notiz zur offiziellen Einreichung steht in [docs/FDROID_EINREICHUNG_DE.md](docs/FDROID_EINREICHUNG_DE.md).
Ein vorbereiteter Submission-Text liegt in [docs/FDROID_SUBMISSION_TEMPLATE_EN.md](docs/FDROID_SUBMISSION_TEMPLATE_EN.md).

## Release-Signing (nur fuer Maintainer)

Fuer Release-Builds wird eine lokale `keystore.properties` benoetigt.
Diese Datei ist NICHT Teil des Repositories und darf niemals committed werden.

1. `keystore.properties.example` nach `keystore.properties` kopieren.
2. Die Platzhalter durch echte Werte ersetzen.
3. Sicherstellen, dass `.gitignore` die Datei ausschliesst.

**Wichtig:**
- `keystore.properties` enthaelt Signing-Passwoerter und darf nicht versioniert werden.
- Der Release-Key (`*.jks`) muss ausserhalb des Repositories verwaltet werden.
- Falls `keystore.properties` bereits geteilt wurde, gelten die enthaltenen Daten als kompromittiert.
- Debug-Builds benoetigen KEIN `keystore.properties` und funktionieren ohne Signing-Konfiguration.
- Ohne `keystore.properties` erzeugt `assembleRelease` eine unsignierte APK (kontrolliert, kein Fehler).
  Fuer eine signierte Release-APK muss `keystore.properties` lokal vorhanden sein.

**R8 / Minify:**
- `isMinifyEnabled` und `isShrinkResources` bleiben bewusst deaktiviert.
- Fruehere Aktivierungsversuche haben zu schwerwiegenden Problemen gefuehrt.
- Keine Aenderung ohne separate, explizite Freigabe.

## Hinweis zur Gradle-Konfiguration
Der Wrapper ist fuer diese Maschine bereits vorbereitet und nutzt die lokal abgelegte Gradle-8.7-Distribution.


