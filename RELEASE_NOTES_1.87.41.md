# RSS Reader Android 1.87.41

## Kurzfassung
Medieninhalte in der Artikelansicht werden jetzt einheitlich begrenzt und zentriert dargestellt. Ein Video aus einem Kommentar-Feed wurde bisher rechts abgeschnitten und flach verzerrt angezeigt, weil die Quelle eine feste Breite (640 px) am Container mitliefert. Zusätzlich gilt die Zentrierung nun für alle Medienarten, nicht nur für Bilder. **Keine Datenbankmigration, keine neuen Abhängigkeiten und keine Änderung an Feeds, Filtern oder Navigation.**

## Änderungen

### Artikelansicht — Video-Embeds
- `ui/screens/ArticleReaderScreen.kt`: Feste Breiten-/Höhenangaben am umgebenden `div` (WordPress: `<div style="width: 640px" class="wp-video">`) werden neutralisiert; damit bezieht sich `max-width: 100%` auf den Viewport statt auf den breiten Container.
- Die `video`-Regel wurde von der iframe-Gruppe getrennt: `width: 100%`, `height: auto`, `aspect-ratio: 16/9`, `object-fit: contain` — **ohne** `max-height`-Kappung, die bei voller Breite das Seitenverhältnis verzerrte.
- Der Normalizer entfernt `width`/`height` aus `<video>`-Tags (mehrstufig, weil ein Regex-Durchlauf nur das erste Attribut trifft).

### Artikelansicht — universelle Medienregel
- `picture, video, audio, iframe, canvas, twitterwidget`: `display: block`, `max-width: 100%`, horizontal zentriert.
- `svg`: nur begrenzt (`max-width: 100%`, `height: auto`), nicht auf `display: block` gezwungen — Inline-Icons im Fließtext bleiben unberührt.
- `audio` (Podcast-Feeds): Breite `min(100%, 560 px)`, zentriert.

## Technische Hinweise
- Version: 1.87.41
- Version Code: 170
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert), keine Datenbankmigrationen, keine neuen Abhängigkeiten.

## Validierung
- `testDebugUnitTest --rerun-tasks`: **367 Tests, 0 Fehler, 0 Errors, 1 übersprungen** in 27 Testsuiten; neu: `sanitizeRemovesFixedVideoSizeAttributes` (echtes `wp-video`-Markup) und `sanitizePreservesAudioAndPictureMediaElements`.
- Gerätetest auf vivo V2405A (Android 16) mit Debug-Build 1.87.40: Video (1:54) vollständig, zentriert und im richtigen Seitenverhältnis — vom Nutzer bestätigt; zusätzlich Volltext-Artikel mit Bild geprüft (volle Breite, zentriert, Text fließt normal).
- Release-Build: signierte APK mit JDK-21-Toolchain und `--rerun-tasks`; `dexdump -d` auf `classes2.dex` bestätigt `HomeScreen`/`FeedConfigScreen` = **3298 / 790**.

## F-Droid-Status

- Tag `v1.87.41` am 2026-11-04 gesetzt; Bot-MR **!55823** („bot: Update RSS Reader to 170") am
  **2026-11-05T07:14:52Z** gemergt (fdroiddata-Commit `71081df0`, `CurrentVersion: 1.87.41` / `170`).
- Der F-Droid-Reproduzierbarkeits-Build stand am 2026-11-05 noch aus (Monitor-Log 404); die
  Veröffentlichung im Index folgt im nächsten Build-/Publish-Zyklus.

## Vorbereitung (nicht in dieser Version enthalten)
- Design für das Nachladen von WordPress-Kommentar-Historien liegt als Dokument bei:
  `docs/specs/2026-11-04-wordpress-kommentar-historie-design.md`. **Noch nicht implementiert.**
