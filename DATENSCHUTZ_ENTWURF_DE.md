# Datenschutz Entwurf

Hinweis:

Dies ist ein inhaltlicher Entwurf fuer die spaetere Datenschutz-Seite. Vor Veroeffentlichung sollte daraus eine oeffentlich erreichbare Web-Seite gemacht werden, zum Beispiel auf einer kleinen Website oder GitHub Pages.

## Datenschutz fuer RSS Reader

RSS Reader ist eine Android-App zum Lesen von RSS- und Atom-Feeds.

### Verantwortlich

`Lolo`

Kontakt:

`[hier spaeter Kontakt-E-Mail eintragen]`

### Welche Daten verarbeitet die App?

Die App verarbeitet nur Daten, die fuer die Feed-Funktion technisch noetig sind.

Dazu gehoeren insbesondere:

- vom Nutzer hinzugefuegte Feed-URLs
- importierte OPML-Dateien
- lokal gespeicherte Feed- und Artikelinhalte
- lokal gespeicherte App-Einstellungen

### Werden personenbezogene Daten an den Entwickler gesendet?

Nein. Nach dem aktuellen Stand sendet RSS Reader keine Nutzerdaten an einen eigenen Server des Entwicklers.

### Kommunikation mit Drittanbietern

Die App ruft RSS- und Atom-Feeds direkt von den vom Nutzer eingetragenen Feed-Adressen ab. Dabei findet eine direkte Verbindung zwischen dem Geraet des Nutzers und dem jeweiligen Feed-Anbieter bzw. der Website statt.

Je nach Anbieter koennen dabei technisch notwendige Verbindungsdaten an den jeweiligen Feed-Server uebermittelt werden, zum Beispiel:

- IP-Adresse
- Zeitpunkt des Abrufs
- technische Anfrageinformationen

Auf diese Verarbeitung durch den jeweiligen Feed-Anbieter hat der Entwickler der App keinen direkten Einfluss.

### Benachrichtigungen

Wenn der Nutzer Benachrichtigungen aktiviert, kann die App lokale Benachrichtigungen ueber neue Artikel anzeigen.

### Lokale Speicherung

Die App speichert Daten lokal auf dem Geraet, insbesondere:

- Feed-Liste
- Artikelinhalte
- Favoritenstatus
- Gelesen-/Ungelesen-Status
- Einstellungen

### Analyse, Tracking, Werbung

Nach dem aktuellen Stand verwendet die App:

- keine Werbung
- kein Analytics-SDK
- kein Crash-Reporting-SDK
- kein Nutzerkonto
- keine Anmeldung

### Datenweitergabe

Es erfolgt keine Weitergabe von Nutzerdaten durch den Entwickler an Dritte.

### Rechte der Nutzer

Nutzer koennen lokal gespeicherte Feeds und Inhalte in der App loeschen oder die App deinstallieren.

### Aenderungen

Diese Datenschutzhinweise koennen bei spaeteren App-Updates angepasst werden.

## Data Safety Einschaetzung fuer Google Play

Vorlaeufige technische Einschaetzung auf Basis des aktuellen Quellcodes:

- keine Werbung
- kein Analytics
- kein Login
- keine Cloud-Konten
- keine Standortdaten
- keine Kontakte
- keine Kamera
- kein Mikrofon
- keine Fotos-/Dateiberechtigung fuer normalen Betrieb
- Benachrichtigungsberechtigung vorhanden
- Internetzugriff vorhanden
- Netzwerkstatus vorhanden

Wahrscheinliche Play-Console-Einschaetzung:

- `Data collected`: sehr wahrscheinlich `No`, soweit nur lokale Verarbeitung und direkter Feed-Abruf ohne eigene Datenerhebung des Entwicklers vorliegt
- `Data shared`: `No`

Wichtig:

Diese Play-Angaben sollten vor dem echten Upload noch einmal manuell geprueft werden.


========================================================================================================================