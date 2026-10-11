# RSS Reader Android 1.87.35

## Kurzfassung
Beim Verlassen der Leseransicht wird der zuletzt sichtbare Artikel als gelesen markiert — über alle vier Ausstiegspfade. Ansonsten keine Verhaltensänderungen.

## Änderungen

### Lesefortschritt beim Verlassen
- Zurück-Pfeil (TopAppBar), Zurück-Taste und Systemgeste: `onDispose` des `DisposableEffect` in `ArticleReaderScreen.kt`.
- Home/App im Hintergrund: `Lifecycle.Event.ON_STOP` über einen `LifecycleEventObserver`.
- Ein Helfer (`markVisibleArticleAsReadOnExit`) und ein Guard (`shouldMarkArticleRead`, sitzungsweites Set) für alle Pfade — keine Doppelmarkierung; nie angezeigte Artikel werden nicht markiert.
- `FeedRepository.markReadInBackground(articleId)`: fire-and-forget auf einem Repository-Scope, damit nach dem Composable-Abbau geschrieben werden kann (Fehler werden verschluckt).
- Unverändert: Markieren beim Öffnen, beim waagerechten Weiterwischen, Dialogaktion „Als gelesen", „Alle als gelesen".

## Technische Hinweise
- Version: 1.87.35
- Version Code: 164
- `1.87.34` wurde nie veröffentlicht (beabsichtigter Versionssprung).
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert), keine Datenbankmigrationen.

## Validierung
- testDebugUnitTest (335 Tests, 0 Fehler, 1 übersprungen; `ArticleReaderSwipeTargetTest` mit 11 Tests)
- assembleDebug
- Gerätetest (Nutzer bestätigt): alle vier Ausstiegspfade verhalten sich korrekt.

## Artefakte
- RSS-Reader-v1.87.35-release.apk (13.015.349 Bytes, SHA-256 `56552b34…`)

## Veröffentlichung und F-Droid-Status
- Tag: `v1.87.35` (annotiert), an GitHub und GitLab gepusht.
- GitHub-Release: `RSS Reader 1.87.35` (nicht Draft, nicht Prerelease).
- F-Droid: **nicht veröffentlicht** — JDK-17-Build (siehe 1.87.36).
