# Export-Sicherheitsregeln

## Bei jedem Quelltext-Export zu beachten

### NIEMALS exportieren:
- `keystore.properties` – enthaelt Signing-Passwoerter
- `local.properties` – enthaelt lokale SDK-Pfade
- `*.jks` / `*.keystore` – Signing-Keystores
- `*.p12` – PKCS#12-Keystores
- `*.pem` / `*.key` – Private Schluessel
- `signing/` – gesamtes Signing-Verzeichnis
- `Ausgabe_APK/` – Build-Artefakte
- `build/` / `.gradle/` – Build-Caches
- `!Backups/` – lokale Backups

### Vor Weitergabe eines Exports pruefen:
Den Export-Text nach folgenden Mustern durchsuchen:
- `storePassword`
- `keyPassword`
- `PRIVATE_KEY` (ohne Leerzeichen: private Schluessel)
- `BEGIN_RSA_PRIVATE_KEY` (mit Unterstrich: Beginn eines RSA-Keys)
- `keystore`
- `signingConfig`

### Export-Werkzeuge:
- `tools/export_source_snapshot.ps1` – enthaelt Ausschlussregeln und Sicherheitspruefung
- Vor Nutzung sicherstellen, dass die Ausschlusslisten aktuell sind

### Falls Secrets versehentlich exportiert wurden:
- Die enthaltenen Signing-Daten gelten als kompromittiert
- Neuen Release-Key erzeugen
- Keystore-Passwoerter aendern
- Betroffene Exporte loeschen
