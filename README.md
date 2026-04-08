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
Vor einem Release-Kandidaten zusaetzlich `gradlew.bat assembleRelease` ausfuehren und die Checkliste in [RELEASE_CHECKLIST_RSS_READER_DE.md](D:/Codex/RSS_Reader_Android/rss_reader_full_project/RELEASE_CHECKLIST_RSS_READER_DE.md) verwenden.

## F-Droid Metadaten
Store-Metadaten fuer F-Droid liegen unter `fastlane/metadata/android/`.
Eine kurze Notiz zur offiziellen Einreichung steht in [docs/FDROID_EINREICHUNG_DE.md](docs/FDROID_EINREICHUNG_DE.md).
Ein vorbereiteter Submission-Text liegt in [docs/FDROID_SUBMISSION_TEMPLATE_EN.md](docs/FDROID_SUBMISSION_TEMPLATE_EN.md).

## Hinweis zur Gradle-Konfiguration
Der Wrapper ist fuer diese Maschine bereits vorbereitet und nutzt die lokal abgelegte Gradle-8.7-Distribution.



