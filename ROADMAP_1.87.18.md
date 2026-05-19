# RSS Reader 1.87.18 – Themenliste

Kein Release vor Abschluss des F-Droid-Prozesses für 1.87.17.

## Offen aus 1.87.17

- [ ] F-Droid MR #38595: CI + Maintainer-Feedback abwarten
- [ ] HTTP-Warnung (http://-Feed) auf echtem Gerät visuell prüfen
- [x] ArticleListScreen-Fixes (Key + Shadow) auf Gerät bestätigt – Zittern aber nicht behoben

## ArticleListScreen – Scroll-Stabilität (Analyse + Teilverbesserungen)

**Status: Scroll-Zittern nicht behoben / weitere Analyse nötig.**

Drei risikoarme Verbesserungen bleiben erhalten (verbessern Stabilität/Rendering,
beheben das sichtbare Zittern aber nicht):

- [x] LazyColumn-Key stabilisiert: `key = { _, item -> item.id }`
- [x] Text-Shadow/Glow entfernt (blurRadius=12f)
- [x] contentType ergänzt: `contentType = { _, _ -> "article" }`

Ausschlussdiagnose (alle negativ – Zittern blieb jeweils unverändert):
- PullRefresh deaktiviert
- Overscroll deaktiviert
- ArticleItem-Rendering stark vereinfacht
- Header entfernt
- HorizontalDivider entfernt
- statische Minimal-LazyColumn mit 300 Textzeilen

Weitere sinnvolle Analyseschritte:
- anderes Gerät testen
- Emulator testen
- 60-Hz / 120-Hz vergleichen
- Bildschirmaufnahme analysieren
- ggf. Compose-/Android-Version prüfen

## Mögliche F-Droid-Nachforderungen

- [ ] CI-Fehler beheben, falls erneut rot
- [ ] Metadaten nach Maintainer-Wunsch anpassen
- [ ] Build-Reproduzierbarkeit prüfen, falls verlangt

## UI-Politur

- [x] Scroll-Verhalten in ArticleListScreen analysiert (3 Teilverbesserungen, nicht behoben)
- [x] SearchScreen-Shadow entfernt (konsistent mit ArticleListScreen)
- [x] FeedListScreen-UI-Texte lokalisiert (8 Strings → DE/EN)
- [ ] Ggf. kleine visuelle Politur, falls beim Gerätetest auffällig

## Technisches

- [x] Artikel-Key, Shadow, contentType in dieser Roadmap dokumentiert
- [x] Scroll-Analyse durchgeführt (FeedListScreen/HomeScreen unauffällig)
- [x] Export-/Repo-Hygiene: analytics.settings + .android/ aus Git entfernt
- [x] .gitignore + Exportskript um lokale Tool-Dateien ergänzt

## Blockiert

- Große Features vor F-Droid-Aufnahme nicht sinnvoll
- UI-Umbauten nicht vor stabiler 1.87.17
- Neue Dependencies erst nach F-Droid-Freigabe
