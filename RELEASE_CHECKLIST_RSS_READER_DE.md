# Release-Checkliste RSS Reader

Diese Checkliste ist fuer echte Veroeffentlichungen gedacht:
- F-Droid
- GitHub-Release
- Play Store
- Weitergabe an andere Nutzer

## 1. Vor dem Release

- Der Code ist auf einem stabilen Stand.
- Es gibt keinen bekannten kritischen Fehler.
- Die Versionsnummer ist bewusst gesetzt.
- Der Stand ist lokal per Git gesichert:
  - `git add .`
  - `git commit -m "Release vorbereiten"`
  - optional: `git tag -a vX.Y.Z -m "Release RSS Reader X.Y.Z"` (nur dieses Format — `^v[\d.]+$`; siehe Abschnitt 6)

## 2. Funktions-Test auf dem Geraet

Vor einem Release diese Punkte einmal komplett testen:

- App startet sauber.
- Startbildschirm / Feed-Liste funktioniert.
- Feed anlegen funktioniert.
- Bestehenden Feed bearbeiten funktioniert.
- Feed aktualisieren funktioniert.
- Artikel-Liste oeffnet sich.
- Artikel-Reader funktioniert.
- Blauer Artikel-Header oeffnet die Webseite extern.
- Bildklick im Artikel oeffnet den Artikel extern.
- Suche funktioniert.
- Einstellungen lassen sich speichern.
- OPML-Import funktioniert.
- OPML-Export funktioniert.
- Verschiebemodus der Feeds funktioniert.
- Hintergrundaktualisierung verursacht keinen offensichtlichen Fehler.

## 3. Technische Schnellpruefung

Im Projektordner:

```powershell
cd F:\001_Coding_Projekte\RSS_Reader_Android\rss_reader_full_project
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat exportReleaseApk
```

Wenn alles sauber ist, ist auch der Release-Build einmal technisch gegengeprueft.

## 4. Release bauen

```powershell
cd F:\001_Coding_Projekte\RSS_Reader_Android\rss_reader_full_project
.\gradlew.bat exportReleaseApk --no-daemon --rerun-tasks
```

Voraussetzung: Gradle findet ein **JDK 21** (`kotlin { jvmToolchain(21) }`); Nachweis mit
`.\gradlew.bat -q javaToolchains --no-daemon` (muss `Language Version: 21` zeigen,
`Detected by: Gradle property 'org.gradle.java.installations.paths'`).
`exportReleaseApk` legt die signierte APK in `Ausgabe_APK\` ab
(`assembleRelease` allein laesst sie in `app\build\outputs\apk\release\`).

**Pflicht: `--rerun-tasks` verwenden.** Ein inkrementeller Release-Build kann Bytecode aus einem
frueheren Lauf mit falschem JDK weiterverwenden. Belegt am 16.10.2026: `exportReleaseApk` ohne
`--rerun-tasks` lieferte die JDK-17-Spalte (`HomeScreen` 3576, `FeedConfigScreen` 880), mit
`--rerun-tasks` die korrekte JDK-21-Spalte (3298 / 790). Ein solches Artefakt darf **nicht**
veroeffentlicht werden (F-Droid-Repro-Build wird rot).

Optional fuer Play Store:

```powershell
.\gradlew.bat bundleRelease
```

## 5. Ergebnis pruefen

Im Ordner:

`F:\001_Coding_Projekte\RSS_Reader_Android\Ausgabe_APK`

pruefen, ob vorhanden:

- `RSS-Reader-v...-release.apk`
- optional `RSS-Reader-v...-release.aab`

Zusatzpruefung:

- Release-Version installiert sich sauber.
- App startet auch als Release.
- Keine offensichtlichen Abstuerze direkt nach Start.

### 5a. Artefakt-Nachweise (Pflicht vor dem Tag)

Erst wenn alle vier Nachweise vorliegen, darf getaggt und veroeffentlicht werden:

1. **Tatsaechlich eingesetzte Toolchain** — `.\gradlew.bat -q javaToolchains --no-daemon`
   muss `Language Version: 21` zeigen (nicht nur „vorhanden", sondern vom Build genutzt).
2. **DEX-Kontrolle gegen die Referenzwerte** — aus der fertigen APK `classes2.dex` entpacken und
   `dexdump -d` auswerten:

   | Methode | JDK 21 (richtig) | JDK 17 (falsch) |
   |---|---|---|
   | `HomeScreenKt; HomeScreen` | **3298** insns | 3576 |
   | `FeedConfigScreenKt; FeedConfigScreen` | **790** insns | 880 |

   JDK-17-Werte ⇒ **nicht veroeffentlichen**, Toolchain/Aufruffolge pruefen und mit
   `exportReleaseApk --rerun-tasks` neu bauen.
3. **SHA-256 und Signatur** — `Get-FileHash -Algorithm SHA256` gegen den dokumentierten Wert;
   `apksigner verify --print-certs` muss das Zertifikat
   `2fb2657d3215dce8bc1b5a7ad84cf083ea6f3e3cde242fdb65e21d81a3b61e02` zeigen
   (muss `AllowedAPKSigningKeys` in der fdroiddata-YAML entsprechen).
4. **Test-Report im richtigen Zeitfenster** — `app\build\reports\tests\testDebugUnitTest\` muss
   **zwischen** dem Versions-Bump (`version.properties`) und der APK-Erstellung liegen; zusaetzlich
   pruefen, dass keine Build-Input-Datei juenger ist als die APK.


## 6. F-Droid-Release (Auto-Modus)

Die App ist im offiziellen F-Droid-Katalog und wird dort im **Auto-Modus** gepflegt
(`AutoUpdateMode: Version`, `UpdateCheckMode: Tags ^v[\d.]+$`, Referenz-Metadaten unter
`docs/fdroid/de.lolo.rssreader.yml`). Es gibt keinen eigenen F-Droid-Repo mehr.

Ablauf pro Release:

1. Annotierten Git-Tag `vX.Y.Z` (Format `^v[\d.]+$`, z.B. `v1.87.36`) auf den Release-Commit setzen und an GitHub **und** GitLab pushen.
2. Signierte Release-APK bauen (`exportReleaseApk` — legt sie in `Ausgabe_APK\` ab) und als GitHub-Release-Asset mit dem
   Namen `RSS-Reader-vX.Y.Z-release.apk` unter dem Tag hochladen (z.B. `gh release create vX.Y.Z ...`).
   Dieses Asset wird vom `Binaries:`-Feld der F-Droid-Metadaten erwartet
   (`.../releases/download/v%v/RSS-Reader-v%v-release.apk`).
3. Der `checkupdates`-Bot erkennt das Tag, traegt die Version in `fdroiddata` ein und legt einen Merge Request an;
   F-Droid merged ihn selbst, sobald die CI gruen ist (**`fdroid build` und `check apk` = success**).
   ⚠️ Ein **roter** offen liegender Bot-MR **blockiert** den Zyklus vollstaendig (belegt 11.–16.09.2026). Dann
   Index (`suggestedVersionCode`) und `CurrentVersion` in `fdroiddata` pruefen; ein manueller MR aus dem eigenen
   Fork ist nur der Notnagel (Ablauf: Skill `fdroid-release-rssreader`).
4. Build-Status beobachten: https://monitor.f-droid.org/ (Startseite). Achtung: die App-Uebersicht
   `https://monitor.f-droid.org/builds/de.lolo.rssreader/` liefert **HTTP 404** (geprueft 11.10.2026) —
   Logs je Version: `https://monitor.f-droid.org/builds/log/de.lolo.rssreader/<versionCode>`.

**Der Reproduzierbarkeitsvergleich findet ausschliesslich extern bei F-Droid statt.** Er ist lokal
nicht nachstellbar; die lokalen Nachweise aus Abschnitt 5a sind notwendige, aber **keine hinreichende**
Bedingung. Ein erfolgreicher Repro-Build darf erst behauptet werden, wenn der F-Droid-Job
`fdroid build` **und** `check apk` gruen zeigt (bzw. der Index die neue Version fuehrt).
Ein **roter, offen liegender** Bot-MR blockiert den Zyklus — dann Maintainer um Schliessung bitten,
keinen eigenen MR aufmachen.

## 7. Nach dem Release

- Finalen Stand per Git taggen:

```powershell
git add .
git commit -m "Release vX.Y.Z"
git tag -a vX.Y.Z -m "Release RSS Reader X.Y.Z"
```

- APK, AAB und Quelltext-TXT archivieren.

## 8. Kein Release machen, wenn

- noch ein reproduzierbarer Absturz bekannt ist
- Feed-Aktualisierung unzuverlaessig ist
- Reader/Browser-Link kaputt ist
- Version nur ein Zwischenstand zum Testen ist
- der Build zwar klappt, aber nicht auf echtem Geraet geprueft wurde

## Kurzregel

Debug-Build:
- fuer Entwicklung und Tests

Release-Build:
- fuer stabile Stands, die du wirklich verteilen willst
