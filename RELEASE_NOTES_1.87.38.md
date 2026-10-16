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
- **Noch nicht veröffentlicht** (Stand der Vorbereitung: 2026-10-16). Vorgesehen sind: annotierter Tag `v1.87.38` an GitHub und GitLab, GitHub-Release „RSS Reader 1.87.38" mit dem Asset `RSS-Reader-v1.87.38-release.apk`.
- F-Droid: kein manueller Merge-Request. Die Metadaten führen `AutoUpdateMode: Version` und `UpdateCheckMode: Tags ^v[\d.]+$`; das `Binaries`-Feld erwartet exakt `…/releases/download/v%v/RSS-Reader-v%v-release.apk` — der Dateiname dieses Releases passt dazu. Erwartet wird, dass der `checkupdates`-Lauf die Version nach dem Tag-Push selbstständig nachzieht. Veröffentlichter Stand zum Zeitpunkt der Vorbereitung: **1.87.37 / 166**.
- Ein erfolgreicher F-Droid-Reproduzierbarkeits-Build ist zum jetzigen Zeitpunkt **nicht** nachgewiesen und wird hier nicht behauptet.
