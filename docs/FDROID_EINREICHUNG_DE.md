# F-Droid Einreichung

Der Quellcode fuer die App liegt im Upstream-Repo:

`https://github.com/immerzu/lolo-rssreader-android`

Die Store-Metadaten fuer F-Droid liegen im Repo unter:

`fastlane/metadata/android/`

Vor einer offiziellen Einreichung sollten diese Punkte noch einmal geprueft werden:

* Release-Tag fuer die einzureichende Version vorhanden
* `fastlane`-Metadaten aktuell
* Icon und Screenshots passen zur aktuellen App
* Lizenzdatei vorhanden
* Datenschutz-/Projektseite erreichbar

Wichtiger Hinweis:

Die Uebersetzungsfunktion in der Experiment-/Release-Linie `1.70.x` nutzt einen Google-Webdienst ohne offiziellen API-Key. Fuer eine offizielle F-Droid-Aufnahme sollte diese Funktion voraussichtlich als Anti-Feature `NonFreeNet` deklariert werden.

Nach der Vorbereitung erfolgt die offizielle Einreichung ueber GitLab/F-Droid:

* Submission Queue oder
* Merge Request gegen `fdroiddata`
