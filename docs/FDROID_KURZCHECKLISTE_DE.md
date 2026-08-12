# F-Droid Kurzcheckliste (Auto-Modus)

Die App ist im offiziellen F-Droid-Katalog und wird automatisch aus Git-Tags gebaut.
Diese Liste gilt fuer jedes neue App-Update.

## 1. Version anheben

```powershell
cd F:\001_Coding_Projekte\RSS_Reader_Android\rss_reader_full_project
.\gradlew.bat bumpReleaseVersion
```

## 2. Committen und pushen

- Aenderungen committen (`version.properties` + `app/build.gradle.kts`)
- Push zu GitHub und GitLab

## 3. Tag setzen und pushen

```powershell
git tag -a v1.87.29 -m "Release RSS Reader 1.87.29"
git push origin v1.87.29
```

Tag-Format exakt `vX.Y.Z` (F-Droid erkennt `^v[\d.]+$`).

## 4. Release-APK bauen und als GitHub-Release-Asset hochladen

```powershell
.\gradlew.bat assembleRelease
gh release create v1.87.29 "F:\001_Coding_Projekte\RSS_Reader_Android\Ausgabe_APK\RSS-Reader-v1.87.29-release.apk"
```

Asset-Name exakt `RSS-Reader-v<version>-release.apk` (wird vom `Binaries:`-Feld erwartet).

## 5. Warten und pruefen

- Build-Status: https://monitor.f-droid.org/ (App `de.lolo.rssreader`)
- Katalog: https://f-droid.org/packages/de.lolo.rssreader/

## Wichtig

- Ein eigener F-Droid-Repo (`fdroid_repo` / `fdroid_publish_site` + GitHub Pages) existiert nicht mehr.
- Fuer F-Droid reichen Tag + GitHub-Release-Asset; kein manueller fdroiddata-Branch noetig.
