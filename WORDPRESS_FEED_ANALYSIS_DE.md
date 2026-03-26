# WordPress-Feed-Analyse

Gepruefter Feed:

- `https://ossiblock.wordpress.com/feed/`

Beobachtete Feldzuordnung pro Item:

- Titel: `<title>`
- Autor: `<dc:creator>`
- Datum: `<pubDate>`
- Link: `<link>`
- Inhalt: nur `<description>` vorhanden
- Volltextfeld `<content:encoded>` ist in diesem Feed nicht vorhanden

Auffaelligkeiten dieses konkreten Feeds:

- Jedes Item enthaelt zusaetzlich ein `media:content` mit einem Gravatar-Bild.
- Innerhalb dieses Media-Blocks steht `media:title` mit dem Wert `bgob2013`.
- Dieses `media:title` ist **nicht** der Beitragstitel und darf niemals als Titel des Artikels verwendet werden.
- Die `description` enthaelt nur einen WordPress-Teaser mit einem `Weiterlesen ->`-Link.
- Wenn in diesem Feed Volltext angezeigt werden soll, reicht der Feed selbst nicht aus. Dann muss der Artikel ueber `link` als Webseite nachgeladen werden.

Umgesetzte Parser-Regel fuer WordPress-Feeds:

- Titel = `title`
- Autor = `dc:creator`, sonst `author`
- Inhalt = zuerst `content:encoded`, sonst `description`
- Datum = `pubDate`
- Link = `link`

Zusatz:

- `Weiterlesen ->` wird aus Teaser-Texten entfernt.
- Verschachtelte Media-Felder wie `media:title` werden nicht mehr als direkte Artikelfelder ausgewertet.
