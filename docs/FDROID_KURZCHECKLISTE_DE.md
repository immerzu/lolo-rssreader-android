# F-Droid Kurzcheckliste

Diese Liste kannst du bei jedem neuen App-Update abarbeiten.

## 1. Neue Release-APK bauen

Die neue Datei liegt danach hier:

- `F:\001_Coding_Projekte\RSS_Reader_Android\Ausgabe_APK`

## 2. Release-APK in den lokalen F-Droid-Ordner kopieren

Kopiere die neue `...release.apk` nach:

- `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo\repo`

## 3. F-Droid-Index neu erzeugen

PowerShell:

```powershell
cd F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo
python -m fdroidserver update -c --rename-apks
```

## 4. Oeffentlichen GitHub-Ordner aktualisieren

PowerShell:

```powershell
robocopy F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo\repo F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_publish_site\repo /MIR
```

## 5. GitHub-Repo aktualisieren

Im Repo `lolo-rssreader-fdroid` hochladen bzw. ersetzen:

- `repo`
- `index.html` nur falls geaendert

Quelle:

- `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_publish_site`

## 6. Warten und pruefen

Danach pruefen:

- `https://immerzu.github.io/lolo-rssreader-fdroid/`
- `https://immerzu.github.io/lolo-rssreader-fdroid/repo`

## Wichtig

- Niemals `config.yml` oder `keystore.p12` aus `fdroid_repo` auf GitHub hochladen.
- Auf GitHub kommt nur der Inhalt von `fdroid_publish_site`.
