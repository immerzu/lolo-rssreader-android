[English](README.md) | Deutsch

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
Vor einem Release-Kandidaten zusaetzlich `gradlew.bat assembleRelease` ausfuehren und die Checkliste in [RELEASE_CHECKLIST_RSS_READER_DE.md](RELEASE_CHECKLIST_RSS_READER_DE.md) verwenden.

## F-Droid Metadaten
Store-Metadaten fuer F-Droid liegen unter `fastlane/metadata/android/`.
Eine kurze Notiz zur offiziellen Einreichung steht in [docs/FDROID_EINREICHUNG_DE.md](docs/FDROID_EINREICHUNG_DE.md).
Ein vorbereiteter Submission-Text liegt in [docs/FDROID_SUBMISSION_TEMPLATE_EN.md](docs/FDROID_SUBMISSION_TEMPLATE_EN.md).

## Release-Signing (nur fuer Maintainer)

Fuer Release-Builds wird eine lokale `keystore.properties` benoetigt.
Diese Datei ist NICHT Teil des Repositories und darf niemals committed werden.

`keystore.properties` enthaelt nur nicht-geheime Werte (`storeFile`, `keyAlias`).
Die Signing-Passwoerter werden nirgendwo im Projekt im Klartext gespeichert.
Sie werden zur Buildzeit aus dem betriebssystemgebundenen Anmeldeinformations-Speicher
(Windows Credential Manager) ueber `tools/get-signing-secret.ps1` bezogen
(Ziele: `rssreader_store_password`, `rssreader_key_password`).

1. `keystore.properties.example` nach `keystore.properties` kopieren.
2. `storeFile` und `keyAlias` eintragen.
3. Sicherstellen, dass die beiden Credential-Manager-Eintraege `rssreader_store_password`
   und `rssreader_key_password` existieren. Falls sie aus einem alten Setup noch im
   Klartext vorliegen, einmalig mit `tools/migrate-signing-secrets.ps1` uebertragen.
4. Sicherstellen, dass `.gitignore` die Datei ausschliesst.

**Wichtig:**
- `keystore.properties` darf nicht versioniert werden (verweist auf Signing-Konfiguration).
- Die Signing-Passwoerter liegen ausschliesslich im OS-Credential-Store, nie in Projektdateien.
- Der Release-Key (`*.jks`, z.B. `signing/rss-reader-release.jks`) muss ausserhalb des
  Repositories verwaltet und separat gesichert werden.
- Debug-Builds werden, sofern `keystore.properties` vorhanden ist, mit demSELBEN Release-Key
  signiert wie die Release-APK. Dadurch aktualisiert `adb install -r` eine installierte
  Release-Version ohne Deinstallation. Ohne `keystore.properties` faellt der Debug-Build auf
  den Standard-Debug-Keystore zurueck.
- Ohne `keystore.properties` erzeugt `assembleRelease` eine unsignierte APK (kontrolliert, kein Fehler).
  Fuer eine signierte Release-APK muss `keystore.properties` lokal vorhanden sein.

**R8 / Minify:**
- `isMinifyEnabled` und `isShrinkResources` sind **dauerhaft VERBOTEN** (niemals aktivieren, nicht als Vorschlag anbieten).
- Fruehere Aktivierungsversuche haben zu schwerwiegenden Problemen gefuehrt.

## Hinweis zur Gradle-Konfiguration
Der Wrapper ist fuer diese Maschine bereits vorbereitet und nutzt die lokal abgelegte Gradle-8.11.1-Distribution.
