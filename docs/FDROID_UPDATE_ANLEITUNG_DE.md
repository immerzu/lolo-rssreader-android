# F-Droid-Release aktualisieren (Auto-Modus)

Die App `de.lolo.rssreader` ist im **offiziellen F-Droid-Katalog** und wird dort im
**Auto-Modus** gepflegt. Ein eigener F-Droid-Repo (frueher geplant ueber `fdroid_repo` /
`fdroid_publish_site` und GitHub Pages) existiert NICHT und wird nicht mehr verwendet.

## Ablauf pro Release

1. Version anheben: `.\gradlew.bat bumpReleaseVersion` (im Ordner `rss_reader_full_project`).
2. Aenderungen committen und pushen (GitHub `immerzu/lolo-rssreader-android` und GitLab).
3. Annotierten Git-Tag im Format `vX.Y.Z` setzen und nach GitHub pushen:

   ```powershell
   git tag -a v1.87.36 -m "Release RSS Reader 1.87.36"
   git push origin v1.87.36
   git push gitlab v1.87.36
   ```

   F-Droid `UpdateCheckMode: Tags ^v[\d.]+$` erkennt nur dieses Format.
4. Signierte Release-APK bauen: `.\gradlew.bat exportReleaseApk` (legt sie in `Ausgabe_APK\` ab;
   `assembleRelease` allein laesst sie in `app\build\outputs\apk\release\` liegen).
5. APK als GitHub-Release-Asset unter dem Tag hochladen, Name exakt `RSS-Reader-vX.Y.Z-release.apk`:

   ```powershell
   gh release create v1.87.36 "F:\001_Coding_Projekte\RSS_Reader_Android\Ausgabe_APK\RSS-Reader-v1.87.36-release.apk"
   ```

   Dieses Asset wird vom `Binaries:`-Feld der F-Droid-Metadaten erwartet:
   `https://github.com/immerzu/lolo-rssreader-android/releases/download/v%v/RSS-Reader-v%v-release.apk`.
6. Der `checkupdates`-Bot erkennt das Tag, traegt die Version in `fdroiddata` ein und legt einen Merge Request
   an; **F-Droid merged selbst, sobald die CI gruen ist** (`fdroid build` **und** `check apk` = success).
   ⚠️ Ein **roter**, offen liegender Bot-MR **blockiert** den Zyklus vollstaendig (belegt 11.–16.09.2026:
   fuenf Tage Stillstand auf 1.87.32/161). Wenn nach ~48 h nichts passiert: Index
   (`https://f-droid.org/api/v1/packages/de.lolo.rssreader`) und `CurrentVersion` in `fdroiddata` pruefen.
   Ein manueller MR aus dem eigenen Fork ist nur der Notnagel (Ablauf im Skill `fdroid-release-rssreader`).

## Referenz-Metadaten

- F-Droid-Metadaten des offiziellen Projekts (maßgeblich): `https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/de.lolo.rssreader.yml`
- Lokale Referenzkopie davon: `docs/fdroid/de.lolo.rssreader.yml` (Stand 11.10.2026: 1.87.36 / 165)
- Eigener fdroiddata-Fork (nur fuer Notnagel-MRs): `https://gitlab.com/immerzu46/fdroiddata`
  (der frueher lokale Klon `fdroiddata_fork` wurde am 30.08.2026 geloescht)
- Build-Status beobachten: https://monitor.f-droid.org/ (Startseite; die App-Uebersicht
  `/builds/de.lolo.rssreader/` liefert **404** — Logs je Version nutzen):
  `https://monitor.f-droid.org/builds/log/de.lolo.rssreader/<versionCode>`

## Wichtig

- Git-Tag und signierte Release-APK muessen auf demselben Commit stehen.
- Niemals private Signierdateien veroeffentlichen (`config.yml`, `keystore.p12` – betraf den nicht mehr genutzten eigenen Repo).
