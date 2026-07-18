# RSS Reader Android 1.87.28

## Kurzfassung
Dieses Release härtet die Artikel-Leseransicht, den Feed- und OPML-Parser sowie die Release-Signierkonfiguration. Eine JavaScript-Injection im Artikel-Reader wurde behoben. Die Release-Signiergeheimnisse werden ausschließlich aus dem betriebssystemgebundenen Anmeldeinformations-Speicher bezogen. Es enthält keine riskanten Änderungen an Build-Shrinking, Datenbankmigrationen oder der App-Architektur.

## Änderungen

### Sicherheit
- JavaScript-Injection im Artikel-Reader behoben (Commit `a5e2b28`).
- Feed- und OPML-Parser gehärtet gegen fehlerhafte und bösartige Eingaben (Commit `910996b`).

### Release-Signierung
- Release-Signiergeheimnisse werden zur Buildzeit sicher aus dem OS-Credential-Store bezogen; keine Klartext-Speicherung in Projektdateien (Commit `910996b`).

### Bereinigung
- Interne Aufräumarbeiten in Parser- und Signing-Konfiguration (Commit `910996b`).

## Technische Hinweise
- Version: 1.87.28
- Version Code: 157
- Keine R8-/ProGuard-/Minify-/ShrinkResources-Änderungen (dauerhaft deaktiviert).
- Keine Datenbankmigrationen.
- Keine riskanten Refactorings.
- Release ohne lokale keystore.properties erzeugt eine unsignierte APK.
- Release mit lokaler keystore.properties erzeugt eine signierte APK.

## Validierung
Folgende Aufgaben wurden erfolgreich ausgeführt:

- testDebugUnitTest
- lintRelease
- assembleRelease

## Artefakte
- RSS-Reader-v1.87.28-release.apk

## Veröffentlichung und F-Droid-Status

- Release-Commit: referenziert durch den annotierten Tag `v1.87.28`.
- Tag: `v1.87.28` (annotiert)
- GitHub-Release: `RSS Reader 1.87.28`
- GitHub `main` und GitLab `main` zeigen auf denselben Release-Commit.
- F-Droid: AutoUpdate aktiv (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags ^v[\d.]+$`). Nach Veröffentlichung des Tags auf GitHub erkennt der F-Droid-`checkupdates`-Lauf die neue Version automatisch; kein manueller fdroiddata-Merge-Request erforderlich.
- Öffentlicher F-Droid-Stand zum Release-Zeitpunkt: noch `1.87.27` / `156`; `1.87.28` wird durch den AutoUpdate-Prozess nachgeholt.
