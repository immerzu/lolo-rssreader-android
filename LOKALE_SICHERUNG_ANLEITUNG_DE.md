# Lokale Sicherung

Dieses Projekt hat eine lokale Git-Sicherung. Damit koennen wir spaeter sauber auf funktionierende Staende zurueckspringen.

## Wichtige Befehle

Aktuellen Stand pruefen:

```powershell
git status
git log --oneline --decorate -n 10
git tag --list
```

Wenn ein Stand stabil ist:

```powershell
git add .
git commit -m "Stabiler Stand"
git tag v1.50.xx-yyy
```

Beispiel:

```powershell
git add .
git commit -m "Stabiler Stand vor Reader-Aenderung"
git tag v1.50.11-103
```

## Auf eine bekannte Version zurueckspringen

Nur Quellcode im Arbeitsordner auf einen Tag setzen:

```powershell
git checkout v1.50.11-103 -- .
```

Oder einen separaten Wiederherstellungs-Branch anlegen:

```powershell
git switch -c codex/restore-v1.50.11-103 v1.50.11-103
```

## Empfehlung

- Vor groesseren Aenderungen immer committen.
- Gute APK-Staende immer auch als Git-Tag markieren.
- Keine Signierdateien oder lokalen Build-Dateien mit committen.
