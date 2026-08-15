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
  - optional: `git tag v1.xx.xx-yyy`

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
.\gradlew.bat assembleRelease
```

Wenn alles sauber ist, ist auch der Release-Build einmal technisch gegengeprueft.

## 4. Release bauen

```powershell
cd F:\001_Coding_Projekte\RSS_Reader_Android\rss_reader_full_project
.\gradlew.bat assembleRelease
```

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

## 6. F-Droid-Release (Auto-Modus)

Die App ist im offiziellen F-Droid-Katalog und wird dort im **Auto-Modus** gepflegt
(`AutoUpdateMode: Version`, `UpdateCheckMode: Tags ^v[\d.]+$`, Referenz-Metadaten unter
`docs/fdroid/de.lolo.rssreader.yml`). Es gibt keinen eigenen F-Droid-Repo mehr.

Ablauf pro Release:

1. Git-Tag `vX.Y.Z` (Format `^v[\d.]+$`, z.B. `v1.87.30`) auf den Release-Commit setzen und nach GitHub pushen.
2. Signierte Release-APK bauen (`assembleRelease`) und als GitHub-Release-Asset mit dem
   Namen `RSS-Reader-vX.Y.Z-release.apk` unter dem Tag hochladen (z.B. `gh release create vX.Y.Z ...`).
   Dieses Asset wird vom `Binaries:`-Feld der F-Droid-Metadaten erwartet
   (`.../releases/download/v%v/RSS-Reader-v%v-release.apk`).
3. Die F-Droid-CI erkennt das Tag automatisch (`checkupdates`), baut die Version und veröffentlicht sie.
   Kein manueller fdroiddata-Aenderungsbranch/Merge-Request erforderlich.
4. Build-Status beobachten: https://monitor.f-droid.org/ (App `de.lolo.rssreader`).

## 7. Nach dem Release

- Finalen Stand per Git taggen:

```powershell
git add .
git commit -m "Release vX.YY.ZZ"
git tag vX.YY.ZZ-AAA
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
