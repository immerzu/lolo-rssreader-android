# RSS Reader Android 1.87.32

## Kurzfassung
Drei neue Funktionen: Volltext der Originalseite in der Artikelansicht, Markieren als gelesen beim Durchlaufen der Artikelliste und Teilen von Artikeln über das Android-Teilen-Menü.

## Änderungen

### Volltext der Originalseite
- Neue Klasse `data/network/ArticleFullTextExtractor.kt` (jsoup 1.18.1, HTTPS-only, 3-MB-Limit).
- In der Leseransicht ein einziges Blatt-Symbol: der erste Tipp lädt (Spinner), weitere Tipps schalten ohne erneutes Laden zwischen Volltext und Kurzfassung um.
- Fehler oder fehlender Link: stiller Rückfall auf den Feed-Inhalt (kein Banner, keine Meldung).

### Lesefortschritt
- Beim Durchlaufen der Artikelliste werden Artikel als gelesen markiert; ausgesetzt beim Filter „nur ungelesen".
- Unverändert: Markieren beim Öffnen eines Artikels, Dialogaktion „Als gelesen", „Alle als gelesen".

### Teilen
- `ui/ArticleSharing.kt` (`ACTION_SEND` + `Intent.createChooser`), Einstiege in der Leseransicht und im Artikel-Dialog der Liste.

## Technische Hinweise
- Version: 1.87.32
- Version Code: 161
- Neue Abhängigkeit: `org.jsoup:jsoup:1.18.1` (Apache-2.0, keine transitiven Abhängigkeiten).
- Keine R8-/ProGuard-/Minify-/ShrinkResources-Änderungen (dauerhaft deaktiviert).
- Keine Datenbankmigrationen.

## Validierung
- testDebugUnitTest (330 Tests, 0 Fehler, 1 übersprungen)
- assembleDebug, lintDebug

## Artefakte
- RSS-Reader-v1.87.32-release.apk (13.016.945 Bytes, SHA-256 `e5bbb860…`)

## Veröffentlichung und F-Droid-Status
- Tag: `v1.87.32` (annotiert), an GitHub und GitLab gepusht.
- GitHub-Release: `RSS Reader 1.87.32` (nicht Draft, nicht Prerelease).
- F-Droid: **veröffentlicht** — 1.87.32/161 war bis 1.87.36 die letzte im Index verfügbare Version.
