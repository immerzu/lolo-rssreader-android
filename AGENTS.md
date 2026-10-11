# AGENTS.md – RSS Reader (Android)

## Projekt
- Android-App in Kotlin + Jetpack Compose, Package `de.lolo.rssreader`, Application-ID `de.lolo.rssreader`
- RSS/Atom-Reader mit Feed-Verwaltung, OPML-Import/Export, Artikel-Reader, Suche, Hintergrundaktualisierung
- Sprache: Kotlin 1.9.24, Compose BOM 2024.06, Material3

## Ordnerstruktur
- `app/src/main/java/de/lolo/rssreader/` – Hauptquellcode
  - `data/` – Room-Datenbank (Entities, DAOs, AppDatabase), Netzwerk (FeedFetcher, FeedParser), OPML, Repository, Einstellungen
  - `ui/` – Compose-UI (Screens, Navigation, Theme, UiFormatters)
  - `sync/` – WorkManager (BackgroundRefreshWorker) und RefreshScheduler
  - `notifications/` – ArticleUpdateNotifier
  - `debug/` – DebugLogger
- `app/src/test/` – Unit-Tests (JUnit 4 + Robolectric)
- `docs/` – F-Droid-Einreichung, Release-Vorgaben
- `tools/` – PowerShell/Cmd-Log-Helfer für Gerätetests
- `fastlane/metadata/android/` – Store-Metadaten für F-Droid
- `signing/` – Keystore (via `keystore.properties`); **gitignored** und nicht Teil des Repos, muss ausserhalb gesichert werden

## Build & Ausführung
- Build-Tool: Gradle 8.11.1 (Wrapper `gradlew.bat`)
- Android Gradle Plugin 8.10.0, compileSdk 36, minSdk 26, Java 17 (Bytecode-Major 61)
- Kotlin-Toolchain fest auf **JDK 21** verankert (`kotlin { jvmToolchain(21) }` in `app/build.gradle.kts`).
  Ohne auffindbares JDK 21 bricht jeder Build ab mit „No locally installed toolchains match".
  Nachweis: `gradlew.bat -q javaToolchains --no-daemon` (muss „Language Version: 21" zeigen)
- Debug-Build: `gradlew.bat assembleDebug` (exportiert APK nach `../Ausgabe_APK/`)
- Release-Build: `gradlew.bat exportReleaseApk` (signiert und exportiert nach `../Ausgabe_APK/`;
  `assembleRelease` allein laesst die APK in `app/build/outputs/apk/release/` liegen)
  (Signierung: Passwörter aus dem Windows-Credential-Manager via `tools/get-signing-secret.ps1`; `keystore.properties` enthält nur `storeFile`/`keyAlias`)
- Tests: `gradlew.bat testDebugUnitTest`
- Android Studio starten: `start-android-studio.ps1`

## Versionierung
- Version ausschließlich in `version.properties` (VERSION_CODE, VERSION_NAME)
- Gradle-Task `bumpReleaseVersion` erhöht Version und schreibt ausschließlich `version.properties`
- Git-Tags für Releases: `v<major>.<minor>.<patch>` (z.B. `v1.87.12`)

## Release-Prozess
- Checkliste: `RELEASE_CHECKLIST_RSS_READER_DE.md`
- Vor Release: Funktionstest auf Gerät, Unit-Tests, `assembleDebug` + `exportReleaseApk` (signiert, nach `../Ausgabe_APK/`; `assembleRelease` allein laesst die APK in `app/build/outputs/apk/release/`)
- Release-APK erscheint unter `../Ausgabe_APK/RSS-Reader-v...-release.apk`

## F-Droid-Vorgehen
- `AutoUpdateMode: Version` + `UpdateCheckMode: Tags ^v[\d.]+$`: Der `checkupdates`-Bot traegt die neue Version
  selbst ein und legt einen Merge Request an; **einen Bot-MR merged F-Droid selbst, sobald die CI gruen ist**
  (`fdroid build` **und** `check apk` = success).
- ⚠️ Ein **roter, offen liegender Bot-MR blockiert** den Zyklus vollstaendig (belegt 11.–16.09.2026: fuenf Tage
  Stillstand auf 1.87.32/161). Dann Index (`https://f-droid.org/api/v1/packages/de.lolo.rssreader`) und
  `CurrentVersion` in `fdroiddata` pruefen; ein manueller MR aus dem eigenen Fork ist **nur der Notnagel**
  (so geschehen bei !49133, gemergt am 16.09.2026).
- Die Release-APK muss mit **JDK 21** entstehen (Toolchain ist fixiert) — sonst scheitert der
  Reproducible-Build-Vergleich gegen das `Binaries`-Asset.
- Details: `RELEASE_CHECKLIST_RSS_READER_DE.md` Abschnitt 6 und `memory/plattform_repositories.md` Abschnitt 9.

## Code-Stil
- Kotlin, kein Java-Code mehr
- Compose-UI mit Navigation-Compose, Material3-Theming in `ui/theme/RssReaderTheme.kt`
- Datenzugriff über Repository-Pattern: `FeedRepository` + `SettingsRepository`
- Room-Datenbank mit KSP (Schema-Export nach `app/schemas/`)
- Neuer Code sollte bestehende Patterns aus `data/` und `ui/` folgen
- Bei Neuerungen Tests unter `app/src/test/` ergänzen (JUnit 4, Robolectric)

## Git-Hinweise
- Arbeitsverzeichnis: `F:\001_Coding_Projekte\RSS_Reader_Android\rss_reader_full_project`
- Remotes: `origin` = https://github.com/immerzu/lolo-rssreader-android.git (oeffentlich),
  `gitlab` = https://gitlab.com/immerzu46/lolo-rssreader-android.git (privat) — Commits, Tags und Releases
  immer an **beide** Remotes pushen
- Vor Änderungen commiten, stabile Stände taggen (siehe `_archiv\GIT_MERKLISTE_KURZ_DE.txt`)
- Keine sensiblen Daten committen: `keystore.properties`, `local.properties`

## _archiv (Altlasten — nicht Teil des aktiven Projekts)
- `_archiv\` (Repo-Wurzel) und `docs\archiv\`: abgelegte Einzel- und Duplikatdokumente
  (u. a. `RELEASE_CHECKLIST.md`, `RELEASE_CHECKLIST_KURZ_DE.txt`, `PLAY_RELEASE_CHECKLIST_DE.md`,
  `ROADMAP_1.87.18.md`, `GIT_MERKLISTE_KURZ_DE.txt`, `EXPORT_SECURITY.md`, `LOGCAT_ANLEITUNG_RSS_READER_DE.md`,
  `DATENSCHUTZ_ENTWURF_DE.md`, `STORE_LISTING_DRAFT_DE.md`, `WORDPRESS_FEED_ANALYSIS_DE.md`,
  `GITHUB_PAGES_ANLEITUNG_DE.md`, `FDROID_GITHUB_PAGES_ANLEITUNG_DE.md`).
- Workspace-Archiv (ausserhalb des Repos): `F:\001_Coding_Projekte\RSS_Reader_Android\_archiv\` mit Altlasten
  wie Log-Dateien, Prompt-Dateien und `verify_tmp\`.
- **Regel:** Was im `_archiv` liegt, ist **nicht** Teil des aktiven Projekts und wird nicht mehr gepflegt.
  Beide Repo-Ordner sind per `.gitignore` aus dem Git-Index genommen (physisch bleiben die Dateien erhalten).
- **Keine neuen Ablagen ohne ausdrueckliche Nutzerfreigabe**; im `_archiv` wird nichts geloescht.

## Was nicht zu tun ist
- Keine neue Gradle-, AGP- oder Kotlin-Version ohne ausdrückliche Anweisung einführen
- Keine Library ohne Grund hinzufügen
- `keystore.properties` / `local.properties` niemals ändern oder committen
- Keine Build-Konfiguration ändern, ohne zu verstehen, was Versionierung, Signing und Export beeinflusst
- R8/ProGuard/Minify/ShrinkResources/Obfuscation sind **dauerhaft VERBOTEN** (niemals aktivieren, nicht als Vorschlag anbieten)

## Änderungsrichtlinie
- **Nur minimale, gezielte Änderungen vornehmen** – keine unnötigen Refactorings, Formatierungen oder Datei-Umstrukturierungen
- Immer den kleinstmöglichen Eingriff wählen, der das Problem löst
- Keine Dateien anfassen, die nicht direkt mit der Aufgabe zu tun haben
- Bestehenden Code-Stil und Patterns beibehalten, auch wenn sie nicht den persönlichen Vorlieben entsprechen

