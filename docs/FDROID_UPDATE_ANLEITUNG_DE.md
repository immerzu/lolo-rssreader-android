# F-Droid-Repo aktualisieren

Lokale Arbeitsordner:

- privat: `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo`
- oeffentlich zum Hochladen: `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_publish_site`

So spielst du spaetere App-Updates ein:

1. Neue signierte Release-APK bauen.
2. Die neue APK nach `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo\repo` kopieren.
3. Im Ordner `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo` ausfuehren:
   - `python -m fdroidserver update -c --rename-apks`
4. Danach den Ordner `repo` aus `fdroid_repo` erneut nach `fdroid_publish_site` kopieren.
5. Optional auch `index.html` im Ordner `fdroid_publish_site` aktualisieren, falls du den Text oder Fingerprint anpassen willst.
6. Auf GitHub im Repo `lolo-rssreader-fdroid` die geaenderten Dateien committen und pushen.

Wichtig:

- `config.yml` und `keystore.p12` bleiben privat.
- Auf GitHub kommt nur der Inhalt von `fdroid_publish_site`.
- Repo-URL:
  - `https://immerzu.github.io/lolo-rssreader-fdroid/repo`
- Fingerprint:
  - `E2 8D B1 9F 26 29 84 19 98 91 FE 7B B9 8D 80 83 09 BB 4F 05 1E 8A 7E F5 B4 4D 9D 30 E5 D4 72 ED`

Was du spaeter normalerweise anfasst:

- `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo\metadata\de.lolo.rssreader.yml`
  Hier stehen Name, Summary, Beschreibung und Web-Links der App.
- `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo\config.yml`
  Hier stehen Repo-Name, Repo-URL und private Signierdaten.

Die Website, die Leute spaeter sehen, liegt hier:

- `https://immerzu.github.io/lolo-rssreader-fdroid/`
