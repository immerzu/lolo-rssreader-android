# F-Droid Kurzcheckliste (Auto-Modus)

Die App ist im offiziellen F-Droid-Katalog und wird automatisch aus Git-Tags gebaut.
Diese Liste gilt fuer jedes neue App-Update.

## 1. Version anheben

```powershell
cd F:\001_Coding_Projekte\RSS_Reader_Android\rss_reader_full_project
.\gradlew.bat bumpReleaseVersion
```

## 2. Committen und pushen

- Aenderungen committen (nur `version.properties`)
- Push zu GitHub und GitLab

## 3. Tag setzen und pushen

```powershell
git tag -a v1.87.36 -m "Release RSS Reader 1.87.36"
git push origin v1.87.36
```

Tag-Format exakt `vX.Y.Z` (F-Droid erkennt `^v[\d.]+$`).

## 4. Release-APK bauen und als GitHub-Release-Asset hochladen

```powershell
.\gradlew.bat exportReleaseApk
gh release create v1.87.36 "F:\001_Coding_Projekte\RSS_Reader_Android\Ausgabe_APK\RSS-Reader-v1.87.36-release.apk"
```

Asset-Name exakt `RSS-Reader-v<version>-release.apk` (wird vom `Binaries:`-Feld erwartet).
`exportReleaseApk` legt die signierte APK in `Ausgabe_APK\` ab.

## 5. Warten und pruefen

- Build-Status: https://monitor.f-droid.org/ (App `de.lolo.rssreader`); Log je Version:
  `https://monitor.f-droid.org/builds/log/de.lolo.rssreader/<versionCode>`
- Katalog: https://f-droid.org/packages/de.lolo.rssreader/
- Index/Version: https://f-droid.org/api/v1/packages/de.lolo.rssreader (`suggestedVersionCode`)

## Wichtig

- Ein eigener F-Droid-Repo (`fdroid_repo` / `fdroid_publish_site` + GitHub Pages) existiert nicht mehr.
- Fuer F-Droid reichen Tag + GitHub-Release-Asset; normalerweise ist **kein** manueller fdroiddata-Branch noetig.
- Ausnahme: Liegt ein **roter** Bot-MR offen, **blockiert** er den AutoUpdate-Zyklus (belegt 11.–16.09.2026).
  Dann erst pruefen, ob der Index nachzieht, und nur als Notnagel einen MR aus dem eigenen Fork stellen
  (Ablauf: Skill `fdroid-release-rssreader`).
