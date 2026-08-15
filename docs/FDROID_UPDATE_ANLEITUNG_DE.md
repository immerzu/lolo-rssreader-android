# F-Droid-Release aktualisieren (Auto-Modus)

Die App `de.lolo.rssreader` ist im **offiziellen F-Droid-Katalog** und wird dort im
**Auto-Modus** gepflegt. Ein eigener F-Droid-Repo (frueher geplant ueber `fdroid_repo` /
`fdroid_publish_site` und GitHub Pages) existiert NICHT und wird nicht mehr verwendet.

## Ablauf pro Release

1. Version anheben: `.\gradlew.bat bumpReleaseVersion` (im Ordner `rss_reader_full_project`).
2. Aenderungen committen und pushen (GitHub `immerzu/lolo-rssreader-android` und GitLab).
3. Annotierten Git-Tag im Format `vX.Y.Z` setzen und nach GitHub pushen:

   ```powershell
   git tag -a v1.87.30 -m "Release RSS Reader 1.87.30"
   git push origin v1.87.30
   ```

   F-Droid `UpdateCheckMode: Tags ^v[\d.]+$` erkennt nur dieses Format.
4. Signierte Release-APK bauen: `.\gradlew.bat assembleRelease`.
5. APK als GitHub-Release-Asset unter dem Tag hochladen, Name exakt `RSS-Reader-vX.Y.Z-release.apk`:

   ```powershell
   gh release create v1.87.30 "F:\001_Coding_Projekte\RSS_Reader_Android\Ausgabe_APK\RSS-Reader-v1.87.30-release.apk"
   ```

   Dieses Asset wird vom `Binaries:`-Feld der F-Droid-Metadaten erwartet:
   `https://github.com/immerzu/lolo-rssreader-android/releases/download/v%v/RSS-Reader-v%v-release.apk`.
6. Fertig: Die F-Droid-CI erkennt das Tag automatisch (`checkupdates`), baut die Version und
   veroeffentlicht sie. Kein manueller fdroiddata-Aenderungsbranch / Merge-Request noetig.

## Referenz-Metadaten

- Aktuelle F-Droid-Metadaten (Vorlage): `docs/fdroid/de.lolo.rssreader.yml`
- Lokaler fdroiddata-Fork (fuer die Ersteinreichung): `F:\001_Coding_Projekte\RSS_Reader_Android\fdroiddata_fork`
- Build-Status beobachten: https://monitor.f-droid.org/ (App `de.lolo.rssreader`)

## Wichtig

- Git-Tag und signierte Release-APK muessen auf demselben Commit stehen.
- Niemals private Signierdateien veroeffentlichen (`config.yml`, `keystore.p12` – betraf den nicht mehr genutzten eigenen Repo).
