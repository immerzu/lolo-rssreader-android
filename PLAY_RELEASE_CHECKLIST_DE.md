# Play Release Checkliste

Stand dieses Projekts:

- Paketname / App-ID: `de.lolo.rssreader`
- Release-APK vorhanden
- Release-AAB vorhanden
- lokale Release-Signierung eingerichtet
- `targetSdk = 35`
- `compileSdk = 35`

Release-Dateien:

- APK fuer Tests: `D:\Codex\RSS_Reader_Android\Ausgabe_APK\RSS-Reader-v1.50.01-93-release.apk`
- AAB fuer Google Play: `D:\Codex\RSS_Reader_Android\Ausgabe_APK\RSS-Reader-v1.50.01-93-release.aab`

Wichtige lokale Signierdateien:

- `D:\Codex\RSS_Reader_Android\rss_reader_full_project\signing\rss-reader-release.jks`
- `D:\Codex\RSS_Reader_Android\rss_reader_full_project\keystore.properties`

Vor dem ersten Play-Upload:

1. Keystore-Datei und `keystore.properties` an einen sicheren Ort sichern.
2. Google-Play-Developer-Konto anlegen oder vorhandenes Konto verwenden.
3. In der Play Console eine neue App mit dem Paketnamen `de.lolo.rssreader` erstellen.
4. Als App-Typ `App` waehlen.
5. Standard-Sprache festlegen.
6. App-Name eintragen.
7. Kontakt-E-Mail hinterlegen.
8. Play App Signing aktivieren.
9. Die Release-AAB hochladen.

Store Listing vorbereiten:

1. App-Name
2. Kurzbeschreibung
3. Vollbeschreibung
4. App-Icon
5. Smartphone-Screenshots
6. Datenschutz-URL
7. Kategorie
8. Kontakt-E-Mail

Screenshots:

- echte In-App-Screenshots verwenden
- Startansicht
- Feed-Liste
- Leseransicht
- Einstellungen
- Dunkelmodus zeigen

Inhalt / Richtlinien in der Play Console:

1. Data safety ausfuellen
2. App access nur falls noetig
3. Ads angeben: in dieser App derzeit `Nein`
4. Altersfreigabe-Fragebogen ausfuellen
5. Zielgruppe / Inhalte pruefen
6. Nachrichten / Benachrichtigungen nur angeben, wenn im Formular abgefragt

Empfohlene Testreihenfolge:

1. Release-APK lokal auf eigenem Geraet testen
2. Release-APK an Freunde geben
3. Danach AAB in die interne Testspur der Play Console laden
4. Erst danach geschlossene / offene Tests oder Produktion

Noch sinnvoll vor echter Veroeffentlichung:

1. Android-Gradle-Plugin spaeter auf eine Version anheben, die `compileSdk 35` offiziell stuetzt
2. App auf mindestens 2-3 echten Geraeten testen
3. finale Datenschutz-Seite online veroeffentlichen
4. Store-Texte und Screenshots finalisieren

Interner Merksatz:

- APK = gut fuer direkte Tests
- AAB = fuer Google Play Upload
- derselbe Signierschluessel muss fuer spaetere Updates erhalten bleiben
