# RSS Reader Android 1.87.29

## Kurzfassung
Dieses Release härtet die Feed-URL-Verarbeitung und das Debug-Logging. Ein https→http-Redirect-Downgrade im Feed-Fetcher wurde blockiert, die Network-Security-Policy ist jetzt explizit konfiguriert, ungültige Feed-Schemata (file://, ftp:// u.a.) werden bereits beim Speichern abgelehnt, und potenzielle user:pass@-Zugangsdaten werden in Debug-Logs maskiert. Es enthält keine riskanten Änderungen an Build-Shrinking, Datenbankmigrationen oder der App-Architektur.

## Änderungen

### Sicherheit
- https→http-Redirect-Downgrade blockiert: `OkHttpClient` folgt keine SSL-Redirects mehr (`followSslRedirects(false)`), damit die bewusste HTTPS-only-Regel nicht durch Redirects auf Klartext-HTTP unterlaufen wird (`FeedFetcher.kt`).
- Explizite Network-Security-Policy: `res/xml/network_security_config.xml` mit `cleartextTrafficPermitted="false"` für alle Verbindungen, im Manifest referenziert (zusätzlich zu `usesCleartextTraffic="false"`).
- Feed-URL-Validierung beim Speichern: `FeedRepository.addFeed` prüft jetzt vor dem Einfügen das URL-Schema (nur `http://` und `https://` mit Host); `file://`, `ftp://` u.a. werden mit `RssReaderException.InvalidUrl` abgelehnt (Commit `730cb59`).
- Debug-Log-Maskierung: `user:pass@`-Bestandteile von Feed-URLs werden in allen `FeedFetcher`-Log-Ausgaben als `***:***@` maskiert (nur Logging; die tatsächlich verwendete URL bleibt unverändert).

## Technische Hinweise
- Version: 1.87.29
- Version Code: 158
- Keine R8-/ProGuard-/Minify-/ShrinkResources-Änderungen (dauerhaft deaktiviert).
- Keine Datenbankmigrationen.
- Keine riskanten Refactorings.
- Release-Signierung unverändert über Windows-Credential-Manager (`tools/get-signing-secret.ps1`); ohne lokale `keystore.properties` entsteht eine unsignierte APK.

## Validierung
Folgende Aufgaben wurden erfolgreich ausgeführt:

- testDebugUnitTest
- assembleDebug
- assembleRelease

## Artefakte
- RSS-Reader-v1.87.29-release.apk (GitHub-Release-Asset, für den F-Droid-Reproduzierbarkeits-Check)

## Veröffentlichung und F-Droid-Status

- Release-Commit: `730cb59c35279a2d5385cb375bb3ead91fef7797`
- Tag: `v1.87.29` (annotiert)
- GitHub-Release: `RSS Reader 1.87.29` (mit Asset `RSS-Reader-v1.87.29-release.apk`)
- GitHub `main` und GitLab `main` zeigen auf denselben Release-Commit.
- F-Droid: AutoUpdate aktiv (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags ^v[\d.]+$`). Der Tag `v1.87.29` löst den automatischen Build aus; das hochgeladene Release-Asset entspricht dem `Binaries:`-Muster (`RSS-Reader-v%v-release.apk`) und wird für den Reproduzierbarkeits-Vergleich verwendet.
- Issue #11 („F-Droid build failed", Maintainer linsui) wurde mit dem Upload-Status beantwortet.
