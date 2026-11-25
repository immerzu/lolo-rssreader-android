# Datenschutzerklärung RSS Reader (Entwurf)

**Status: ENTWURF — nicht veröffentlicht.** Stand: 2026-11-25.

> **NICHT ZUR VERÖFFENTLICHUNG VORGESEHEN.** Interne Dokumentation; A29 ist als nicht anwendbar
> geschlossen (Google Play wird nicht genutzt). Eine Veröffentlichung wäre eine neue Entscheidung.
Grundlage: Quellcode-Stand `841b36d` (Version 1.87.46 / 175). Vor einer Veröffentlichung müssen
die mit **[zu bestätigen]** markierten Angaben vom Verantwortlichen geprüft werden.

## 1. Verantwortlicher

Verantwortlich für die App „RSS Reader" (Paket `de.lolo.rssreader`) ist der Entwickler.
Kontakt: kesselflickerei@gmx.de (Adresse aus den Store-Metadaten; **[zu bestätigen]**).
Eine vollständige Postanschrift wird nur benötigt, wenn sie für den Vertriebskanal vorgeschrieben ist
**[zu bestätigen]**.

## 2. Zweck der App

Der RSS Reader abonniert vom Nutzer eingetragene RSS-/Atom-Feeds und zeigt deren Artikel lokal an.
Es gibt keine Benutzerkonten, keine Anmeldung und keine Synchronisation zwischen Geräten.

## 3. Welche Daten verarbeitet werden

### 3.1 Lokal auf dem Gerät gespeichert

- **Abonnierte Feeds:** URL, Titel, optionale Anzeigenamen, Website-/Icon-URL, Sortierung,
  WLAN-Regel.
- **Artikel:** Titel, Link, Veröffentlichungsdatum, Text-/HTML-Inhalt (gekürzt bei sehr großen
  Artikeln), Bild-URLs, Lese- und Favoritenstatus.
- **Einstellungen:** Aktualisierungsintervall, Theme, Schriftgröße, Bildanzeige, Filter je Feed,
  Benachrichtigungsoption.
- **Zwischenspeicher:** Bild-Cache (Coil) und WebView-Cache für dargestellte Inhalte.

Diese Daten verlassen das Gerät nicht. Sie werden gelöscht, wenn der Nutzer sie in der App entfernt
oder die App-Daten löscht bzw. die App deinstalliert.

### 3.2 Netzwerkverbindungen

Die App baut ausschließlich HTTPS-Verbindungen auf (Klartext-HTTP ist technisch gesperrt). Dabei
werden zwangsläufig die **IP-Adresse** des Geräts und ein technischer **User-Agent**
(`RSS-Reader/1.x (+Android)`) an den jeweiligen Server übertragen:

- **Feed-Aktualisierung:** Abruf der vom Nutzer abonnierten Feed-URLs (manuell oder im gewählten
  Intervall, optional nur im WLAN).
- **Volltextansicht:** Nur auf ausdrückliche Nutzeraktion wird die Originalseite des Artikels geladen.
- **Bilder:** Feed-Symbole und Artikelbilder werden direkt vom jeweiligen Quellserver geladen.

Empfänger sind ausschließlich die vom Nutzer selbst abonnierten Feed-/Website-Server. Es gibt keinen
zwischengeschalteten Dienst des Entwicklers, keine Analyse-, Werbe- oder Crash-Reporting-Dienste.

### 3.3 Benachrichtigungen

Neue Artikel können lokal als Benachrichtigung gemeldet werden. Dafür ist die Berechtigung
`POST_NOTIFICATIONS` erforderlich; die Benachrichtigung wird auf dem Gerät erzeugt.

### 3.4 Berechtigungen

- `INTERNET` und `ACCESS_NETWORK_STATE`: Feed-Abruf und Erkennung der Netzwerkart.
- `POST_NOTIFICATIONS` (Android 13+): optionale Hinweise auf neue Artikel.

Weitere Berechtigungen werden nicht angefordert.

### 3.5 Protokollierung

In **Debug-Builds** schreibt die App ein technisches Protokoll in den App-Datenbereich
(maximal 256 KB, Zugangsdaten in URLs werden maskiert). **Release-Builds schreiben keine
Protokolldatei.**

### 3.6 Datensicherung

Automatische Cloud-Sicherung und Geräteübertragung sind deaktiviert (`allowBackup="false"`,
Datenextraktion für alle Bereiche ausgeschlossen).

## 4. Drittanbieter

Die App bindet keine Analyse-, Werbe- oder Tracking-SDKs ein. Beim Bezug über F-Droid oder Google
Play gelten zusätzlich die Datenschutzbestimmungen des jeweiligen Stores.

## 5. Speicherdauer

Lokale Daten bleiben gespeichert, bis der Nutzer sie löscht (Feed entfernen, Einträge löschen,
App-Daten löschen, Deinstallation).

## 6. Rechte der Nutzer

Da alle Inhalte lokal liegen, kann der Nutzer sie jederzeit einsehen und löschen. Für Fragen zur
Verarbeitung ist der oben genannte Kontakt zuständig.

## 7. Änderungen

Diese Erklärung wird angepasst, wenn sich die Datenverarbeitung der App ändert; das Datum oben wird
dann aktualisiert.
