# F-Droid Einreichung

> **Hinweis (Stand 2026-08):** Die App ist inzwischen im offiziellen F-Droid-Katalog und wird
> im Auto-Modus gepflegt. Fuer Updates reichen Git-Tag + GitHub-Release-Asset (siehe
> `FDROID_UPDATE_ANLEITUNG_DE.md`). Die nachfolgenden Schritte betreffen die Ersteinreichung.

Der Quellcode fuer die App liegt im Upstream-Repo:

`https://github.com/immerzu/lolo-rssreader-android`

Die Store-Metadaten fuer F-Droid liegen im Repo unter:

`fastlane/metadata/android/`

Vor einer offiziellen Einreichung sollten diese Punkte noch einmal geprueft werden:

* Release-Tag fuer die einzureichende Version erstellen und pushen
* `fastlane`-Metadaten aktuell
* Icon und Screenshots passen zur aktuellen App
* Lizenzdatei vorhanden
* Source-Repo oeffentlich erreichbar
* Issue-Tracker oeffentlich erreichbar

Nach der Vorbereitung erfolgt die offizielle Einreichung ueber GitLab/F-Droid:

* Submission Queue oder
* Merge Request gegen `fdroiddata`

Empfohlener Weg:

1. Git-Tag `vX.YY.ZZ` auf den einzureichenden Commit setzen und pushen.
2. Die vorbereitete Metadatei unter `docs/fdroid/de.lolo.rssreader.yml` als Basis fuer `fdroiddata/metadata/de.lolo.rssreader.yml` verwenden.
3. Den vorbereiteten englischen Einreichungstext aus `docs/FDROID_SUBMISSION_TEMPLATE_EN.md` in GitLab verwenden.
4. Falls kein direkter Merge Request gewuenscht ist, den gleichen Inhalt in die Submission Queue einstellen.
