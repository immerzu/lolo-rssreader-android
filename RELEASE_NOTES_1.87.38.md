# RSS Reader Android 1.87.38

## Kurzfassung
Neue Funktion aus GitHub Issue #12: Die Schaltfläche „Nur ungelesene Artikel" in der Artikelliste merkt sich ihre Stellung jetzt **dauerhaft und pro Feed**. Jeder Feed hat einen eigenen, unabhängig umschaltbaren Filterzustand, der Navigation, App-Neustart und Prozessneustart übersteht. Bestehende Benutzerdaten bleiben unverändert; neue Feeds zeigen weiterhin alle Artikel.

## Änderungen

### Feedbezogener Ungelesen-Filter (GitHub Issue #12)
- `data/settings/SettingsRepository.kt`: neuer feedbezogener DataStore-Key `show_unread_only_feed_<feedId>` mit `unreadOnlyFilterForFeed(feedId): Flow<Boolean>` und `setUnreadOnlyFilterForFeed(feedId, value)`. Ohne gespeicherten Wert gilt `false` (alle Artikel).
- `data/settings/AppPreferences.kt`: das frühere globale Feld `showUnreadOnly` ist entfernt; der Filterzustand liegt nicht mehr in den allgemeinen App-Einstellungen.
- `ui/RssReaderApp.kt`: liest den Filterzustand je Feed (`remember(feedId)` + Flow) und schreibt Änderungen asynchron; Speicherfehler werden pro Feed geführt und gemeldet, sodass eine Rückmeldung aus einem Feed keinen anderen Feed beeinflusst.
- `ui/screens/ArticleListScreen.kt`: Schaltfläche und Icon-Farbe folgen dem effektiven Zustand (unmittelbarer Toggle-Override, danach der persistierte Wert des Feeds); die Leerzustandsanzeige „Keine ungelesenen Artikel." bleibt unverändert.
- Bestehende Daten: Der bisherige globale Schlüssel `show_unread_only` wird nicht mehr verwendet und **nicht** gelöscht (keine destruktive Bereinigung); er bleibt ungenutzt im DataStore erhalten und bestimmt keinen Feed-Zustand.

## Technische Hinweise
- Version: 1.87.38
- Version Code: 167
- Keine R8-/Minify-Änderungen (dauerhaft deaktiviert), keine Datenbankmigrationen, keine neuen Abhängigkeiten.

## Validierung
- testDebugUnitTest (359 Tests, 0 Fehler, 1 übersprungen; angepasste und neue Tests: feedbezogene Persistenz je Feed, Unabhängigkeit mehrerer Feeds, Bestandsdaten/Alt-Schlüssel, Filterlogik und Zustandsauflösung)
- lintDebug (0 Fehler, 24 Warnungen)
- Gerätetest auf vivo V2405A (Android 16) mit dem Debug-Build 1.87.38/167: Der Nutzer hat die feedbezogene Filterpersistenz bestätigt (jeder Feed behält seine eigene Filterstellung, auch nach vollständigem App-Neustart); die weiteren geprüften Funktionen verhalten sich korrekt.
- Release-Build: signierte APK mit JDK-21-Toolchain erzeugt; JDK-Nachweis am Artefakt (`dexdump -d` auf `classes2.dex`, `insns size` von `HomeScreen`/`FeedConfigScreen` = **3298 / 790** = JDK-21-Spalte).

## Artefakte
- `RSS-Reader-v1.87.38-release.apk` (13.015.889 Bytes, SHA-256 `90e5ba0db61d310b91cc0c8e78a84d37f1f9919e16e264e88773d316cf22c151`)
- Debug-Testbuild: `RSS-Reader-v1.87.38-debug-20261016-070646.apk` (18.820.691 Bytes)
- Signatur (beide): `2fb2657d3215dce8bc1b5a7ad84cf083ea6f3e3cde242fdb65e21d81a3b61e02`

## Veröffentlichung und F-Droid-Status

**Veröffentlicht am 2026-10-16** (Stand dieses Abschnitts: 2026-10-30):
- Annotierter Tag `v1.87.38` (Tag-Objekt `3bf302a10713be8ac66193176c94c19d9a2ec75b` → Commit `a17588b1aa019935966dd7f511b3fa6c8a27e839`), auf **beiden** Remotes.
- GitHub-Release „RSS Reader 1.87.38" (nicht Draft, nicht Prerelease, veröffentlicht 2026-10-16T05:35:18Z) mit Asset `RSS-Reader-v1.87.38-release.apk` (13.015.889 Bytes, `sha256:90e5ba0db61d310b91cc0c8e78a84d37f1f9919e16e264e88773d316cf22c151`).
- **JDK-Nachweis am Artefakt** (`dexdump -d` auf `classes2.dex`): `HomeScreen` = **3298**, `FeedConfigScreen` = **790** — JDK-21-Spalte. Ein erster Release-Build mit JDK 17 (`HomeScreen` = 3576, `FeedConfigScreen` = 880; SHA-256 `5E86D570…`) wurde **verworfen und nicht veröffentlicht**; er liegt außerhalb des Repos unter `..\!old\_release_1_87_38_jdk17_verworfen\`.

**F-Droid: diese Version wurde nicht ausgeliefert** (belegt am 2026-10-30):
- Der Bot erkannte den Tag und legte MR **!52871** („bot: Update RSS Reader to 167") an; gemergt am **2026-10-17T08:11:12Z**, CI-Pipeline `success`.
- Ein Build für 167 lief **nie** an: `https://monitor.f-droid.org/builds/log/de.lolo.rssreader/167` → **HTTP 404** („no logs available for this build").
- Der Build-Eintrag wurde am **2026-10-18** mit dem fdroiddata-Commit `286089aa` (Autor `linsui`, Titel „keep latest") wieder **entfernt**. Der F-Droid-Index führt 167 deshalb nicht; auf 1.87.37/166 folgte direkt **1.87.39/168**.
- Ein Reproduzierbarkeits-Build für 167 ist damit **nicht** nachgewiesen und wird hier nicht behauptet.
