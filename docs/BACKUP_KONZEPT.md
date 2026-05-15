# Backup-Konzept – RSS Reader Android

Stand: 2026-05-10

## 1. Datenquellen

### 1.1 Versionierte Repositories

| Repository | Remote | Branches | Tags |
|---|---|---|---|
| `rss_reader_full_project` | GitHub `immerzu/lolo-rssreader-android` | main, codex/translate-experiment, backup-mr-*, codex/release-* (3 remote) | v1.50.09–v1.87.16 (22) |
| `fdroiddata_fork` | GitLab `immerzu46/fdroiddata` | master, codex/update-*, backup-mr-* | keine |

**Push-Status:** Beide Repos sind auf dem aktuellen Stand (bis auf 5 uncommittete Änderungen im Projekt, siehe 1.2).

### 1.2 Nicht versionierte Arbeitsdateien

| Kategorie | Pfad | Inhalt | Größe |
|---|---|---|---|
| **Keystore** | `signing/rss-reader-release.jks` | F-Droid/GitHub Release-Signatur | ~3 KB |
| **APK-Ausgaben** | `Ausgabe_APK/*.apk` | Debug- und Release-APKs | ~50 MB |
| **Projektdokus** | `*.md`, `*.txt` (Root) | F-Droid-Anleitungen, Release-Stände | ~8 KB |
| **Memory** | `memory/` | Projektkontext-Gedächtnis | ~6 KB |
| **Audit** | `audit_release_comparison/` | Release-Vergleichsdaten | ~18 MB |

### 1.3 Aktuelle uncommittete Änderungen

5 Dateien modifiziert (Härtung + UI):
- `AndroidManifest.xml` – usesCleartextTraffic="false"
- `AppUserAgent.kt` – Version 1.x
- `ArticleReaderScreen.kt` – domStorageEnabled konditional
- `FeedConfigScreen.kt` – HTTPS-Validierung
- `HomeScreen.kt` – Statusanzeigen unter TopAppBar

## 2. Backup-Strategie

### 2.1 Remote-Sicherung (automatisch)

- **GitHub** + **GitLab**: Jeder `git push` sichert den aktuellen Code-Stand
- Tags sind auf Remote unveränderlich (Schutz vor versehentlichem Löschen durch GitHub-Settings prüfen)
- **Empfehlung:** `backup-mr-before-1.87.16-145` als Schutzbranch behalten

### 2.2 Lokale Vollbackups

**Ort:** `!Backups/YYYY-MM-DD_HH-MM-SS_current_state/`

**Inhalt:**
- `rss_reader_full_project/` (ohne .git, .gradle, build/, .idea, signing/)
- `fdroiddata_fork/` (ohne .git)
- `Ausgabe_APK/` (ohne old_Versionen/, Builds_Sicherungen/)
- `memory/`
- `audit_release_comparison/`
- Root-Dokumente (*.md, *.txt, *.png, *.jpg)

**Ausgeschlossen:** `.git`, `.gradle`, `build/`, `.idea`, `signing/`, `old_Versionen/`, `Builds_Sicherungen/`, `android_sdk/`, `fdroid_ci_*`

**Intervall:** Vor jeder Codeänderung, die mehr als 1 Zeile betrifft.

### 2.3 Keystore-Sicherung (KRITISCH)

Die Datei `signing/rss-reader-release.jks` ist **gitignored** und liegt **nicht** in den Vollbackups. Sie muss **separat gesichert** werden:

- **Empfohlen:** Physischer USB-Stick + verschlüsselter Cloud-Speicher
- **Niemals:** In Git committen, in Build-Logs ausgeben, an Dritte weitergeben
- **Passwort:** Getrennt vom Keystore aufbewahren

### 2.4 Remote-Only Branches (Risiko)

10 Branches im `fdroiddata_fork` existieren nur auf GitLab Remote, nicht lokal:
- `clean-fdroid-fix`, `codex/update-*` (3x), `de.lolo.rssreader`,
  `licaon-kter-master-patch-21551`, `rss-reader-1.87.06-fdroid`,
  `rssreader-fdroid-fix`, `test-issuebot` (2x)

**Risiko:** Bei versehentlichem Remote-Prune oder GitLab-Projekt-Löschung sind diese Branches verloren.

**Empfehlung:** Einmalig lokal fetchen, bewerten und entweder löschen oder in Backup-Branch sichern.

## 3. Nicht gesichert / Reproduzierbar

Diese Daten sind ausgeschlossen, da reproduzierbar:

| Artefakt | Wiederherstellung |
|---|---|
| `.gradle/` | `./gradlew build` |
| `build/` | `./gradlew assembleDebug` |
| `.idea/` | Android Studio generiert neu |
| `android_sdk/` | SDK Manager |
| `fdroid_ci_*` | GitLab CI Pipeline |
| `old_Versionen/`, `Builds_Sicherungen/` | Alte Backups, bereits redundant |

## 4. Wiederherstellung

### Vollständige Wiederherstellung

```bash
# 1. Repositories klonen
git clone https://github.com/immerzu/lolo-rssreader-android.git
git clone https://gitlab.com/immerzu46/fdroiddata.git fdroiddata_fork
cd fdroiddata_fork
git remote add upstream https://gitlab.com/fdroid/fdroiddata.git

# 2. Letztes Vollbackup entpacken/bereitstellen (z.B. von USB-Stick)
cp -r !Backups/2026-05-10_*/memory/ ./memory/
cp -r !Backups/2026-05-10_*/Ausgabe_APK/ ./Ausgabe_APK/
cp !Backups/2026-05-10_*/{*.md,*.txt} ./

# 3. Keystore wiederherstellen (von separatem Sicherungsmedium)
mkdir signing
cp /pfad/zum/keystore-backup/rss-reader-release.jks signing/

# 4. Build testen
cd rss_reader_full_project
./gradlew assembleDebug
```

### Nur Code wiederherstellen (kein Keystore)

```bash
git clone https://github.com/immerzu/lolo-rssreader-android.git
cd lolo-rssreader-android
./gradlew assembleDebug
# APK ist lauffähig, aber nicht F-Droid-signierbar ohne Keystore
```

## 5. Backup-Prüfung

Nach jeder Sicherung prüfen:
- [ ] Backup-Verzeichnis existiert
- [ ] Dateianzahl plausibel (~77.000)
- [ ] Schlüsseldateien lesbar (build.gradle.kts, Manifest, FeedFetcher.kt)
- [ ] APK-Dateien vorhanden
- [ ] Keine leeren Verzeichnisse

## 6. Sicherheitsregeln

- Keystore-Passwort **niemals** in Klartext speichern oder committen
- Keine `.jks`, `.keystore`, `.p12` Dateien in Repos
- `signing/` ist in `.gitignore` → nie mit `git add -f` aushebeln
- Build-Ausgaben auf Secrets prüfen vor Veröffentlichung
