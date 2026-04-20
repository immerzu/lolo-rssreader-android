# Logcat-Anleitung fuer RSS Reader

Diese Anleitung hilft dabei, einen echten Absturz der App sauber aufzuzeichnen, damit die Ursache gezielt behoben werden kann.

## Voraussetzung

- Android-Handy per USB mit dem PC verbinden
- Entwickleroptionen auf dem Handy aktivieren
- `USB-Debugging` auf dem Handy einschalten
- `adb` muss auf dem PC verfuegbar sein

## 1. Geraet pruefen

PowerShell oeffnen und ausfuehren:

```powershell
adb devices
```

Erwartung:
- dein Geraet erscheint in der Liste
- Status sollte `device` sein

Wenn `unauthorized` erscheint:
- Display des Handys entsperren
- USB-Debugging-Freigabe bestaetigen

## 2. Altes Log leeren

Vor dem Test das bisherige Log leeren:

```powershell
adb logcat -c
```

## 3. Absturz gezielt nachstellen

- RSS Reader starten
- genau die Schritte ausfuehren, die zum Absturz fuehren
- direkt nach dem Absturz nichts weiter in anderen Apps machen

## 4. Log speichern

Danach das Log in eine Datei schreiben:

```powershell
adb logcat -d > F:\Codex\RSS_Reader_Android\Ausgabe_APK\rss-reader-crash-log.txt
```

Die Datei liegt dann hier:

[rss-reader-crash-log.txt](F:\Codex\RSS_Reader_Android\Ausgabe_APK\rss-reader-crash-log.txt)

## 5. Was fuer die Analyse wichtig ist

Im Log sind vor allem diese Begriffe wichtig:

- `FATAL EXCEPTION`
- `AndroidRuntime`
- `Process: com.example.rssreader`
- `Caused by:`

Wenn du mir die Datei gibst, kann ich den eigentlichen Absturzpunkt meist direkt aus dem Stacktrace herauslesen.

## 6. Schneller Direktbefehl

Wenn du alles in einem Ablauf machen willst:

```powershell
adb logcat -c
```

Dann Absturz nachstellen, danach:

```powershell
adb logcat -d > F:\Codex\RSS_Reader_Android\Ausgabe_APK\rss-reader-crash-log.txt
```

## 7. Optional: nur relevante Zeilen ansehen

```powershell
Get-Content F:\Codex\RSS_Reader_Android\Ausgabe_APK\rss-reader-crash-log.txt | Select-String "FATAL EXCEPTION|AndroidRuntime|com.example.rssreader|Caused by"
```

## Kurzfassung

1. `adb devices`
2. `adb logcat -c`
3. Absturz nachstellen
4. `adb logcat -d > F:\Codex\RSS_Reader_Android\Ausgabe_APK\rss-reader-crash-log.txt`
5. Datei an mich weitergeben
