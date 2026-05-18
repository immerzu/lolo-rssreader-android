# Release-Checkliste RSS Reader

Kurz und praktisch – vor jedem Release einmal durchgehen.

## 1. Vor dem Release

- [ ] `git status` – sicherstellen, dass nur gewollte Änderungen vorhanden sind
- [ ] Keine Secrets im Commit: `keystore.properties`, `local.properties`, `*.jks`, `signing/` dürfen nicht getrackt sein
- [ ] `keystore.properties` ist nur lokal vorhanden, NICHT in Git
- [ ] `version.properties` enthält die gewünschte Release-Version
- [ ] `README.md` und Doku sind aktuell
- [ ] `git diff` zeigt keine unerwarteten Änderungen

## 2. Tests

```powershell
cd F:\Codex\RSS_Reader_Android\rss_reader_full_project
.\gradlew.bat clean testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat exportReleaseApk
.\gradlew.bat exportReleaseBundle
```

Alle Tasks müssen grün sein.

## 3. Artefakte prüfen

- [ ] `F:\Codex\RSS_Reader_Android\Ausgabe_APK\` enthält die neuen Dateien
- [ ] Dateinamen enthalten die korrekte Version (z. B. `RSS-Reader-v1.87.16-release.apk`)
- [ ] Keine APK/AAB versehentlich in Git
- [ ] APK kurz auf Gerät oder Emulator starten und Rauchtest machen

## 4. Sicherheit

- [ ] Quelltext-Export NUR über `tools/export_source_snapshot.ps1` erzeugen
- [ ] Export enthält keine `keystore.properties`, `local.properties`, `*.jks`, `signing/`
- [ ] Export enthält keine Build-Artefakte (`build/`, `.gradle/`, `Ausgabe_APK/`)
- [ ] Falls Signing-Daten jemals geteilt wurden: neuen Release-Key erzeugen

## 5. Nach dem Release

- [ ] `.\gradlew.bat bumpReleaseVersion` für den nächsten Release ausführen
- [ ] Geänderte Dateien committen (version.properties + build.gradle.kts)
- [ ] Erzeugte Artefakte sicher archivieren
- [ ] Changelog/Notizen aktualisieren

## WICHTIG

**Keine R8-/Minify-/ShrinkResources-Aktivierung ohne separate, explizite Freigabe.**
Frühere Aktivierungsversuche haben zu schwerwiegenden Problemen geführt.
