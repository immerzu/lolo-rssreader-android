> **Hinweis (Stand 2026-08):** Der hier beschriebene eigene F-Droid-Repo ueber GitHub Pages
> (`fdroid_repo` / `fdroid_publish_site` / `lolo-rssreader-fdroid`) wurde NICHT eingerichtet und
> wird nicht verwendet. Die App ist offiziell bei F-Droid im Auto-Modus (Git-Tag + GitHub-Release-Asset).
> Siehe `FDROID_UPDATE_ANLEITUNG_DE.md`. Die nachfolgenden Schritte sind als historische Anleitung
> erhalten.

# Eigenes F-Droid-Repo ueber GitHub Pages

Dein lokales F-Droid-Repo liegt hier:

- `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo`

Wichtig:

- **Nicht** den ganzen Ordner `fdroid_repo` auf GitHub hochladen.
- In `fdroid_repo` liegen private Dateien wie:
  - `config.yml`
  - `keystore.p12`
- Diese Dateien duerfen **niemals** oeffentlich werden.

Fuer GitHub Pages nur diesen vorbereiteten Ordner verwenden:

- `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_publish_site`

Dieser Ordner enthaelt nur die oeffentlichen Dateien deines Repos.

Empfohlenes GitHub-Repo:

- `lolo-rssreader-fdroid`

Deine spaetere URL ist dann:

- `https://immerzu.github.io/lolo-rssreader-fdroid/repo`

Schritte:

1. Erstelle auf GitHub ein neues oeffentliches Repo namens `lolo-rssreader-fdroid`.
2. Lade **nur den Inhalt** von `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_publish_site` hoch.
3. Oeffne auf GitHub `Settings > Pages`.
4. Stelle ein:
   - `Deploy from a branch`
   - Branch `main`
   - Folder `/root`
5. Speichern.
6. Nach ein paar Minuten sollte diese URL erreichbar sein:
   - `https://immerzu.github.io/lolo-rssreader-fdroid/repo`

Danach kannst du das Repo in der F-Droid-App hinzufuegen.

Fingerprint des Repo-Schluessels:

- `E2 8D B1 9F 26 29 84 19 98 91 FE 7B B9 8D 80 83 09 BB 4F 05 1E 8A 7E F5 B4 4D 9D 30 E5 D4 72 ED`

Zum Aktualisieren spaeter:

1. Neue signierte Release-APK in `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo\repo` legen.
2. In `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_repo` ausfuehren:
   - `python -m fdroidserver update -c --rename-apks`
3. Den Inhalt von `F:\001_Coding_Projekte\RSS_Reader_Android\fdroid_publish_site` erneut auf GitHub hochladen.
