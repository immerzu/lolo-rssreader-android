# RSS Reader Android 1.87.44

## Kurzfassung
Der Eintrag „Favoriten" auf der Startseite hat ein eigenes Icon: den Favoritenstern, der die
Icon-Fläche ausfüllt, mit dem „F" in der Mitte. Keine weiteren Änderungen.

## Änderungen
- Neues Icon für den virtuellen Feed „Favoriten" (`FeedListScreen.kt`): statt des Buchstaben-Fallbacks
  wird `FavoriteFeedIcon` gezeichnet — fünfzackiger Stern mit radialem Grauverlauf, heller Kontur und
  „F" im Flächenschwerpunkt.
- Die Zacken berühren den Rand der 22-dp-Fläche, ohne sie zu überschreiten; das „F" sitzt nicht in der
  Mitte der Bounding-Box (dort stünde es rund 5 % der Kante zu hoch), sondern im Flächenschwerpunkt
  des Sterns.
- Farben sind feste Grauwerte (heller Kern `#FAFAFA`, Zacken `#787878`, Kontur weiß, „F" `#101010`),
  damit das Icon in hellem und dunklem Modus gleich aussieht.

## Technische Hinweise
- Neue Dateien: `ui/screens/FavoriteStarGeometry.kt` (Sternpolygon, Normierung, Schwerpunkt),
  `ui/screens/FavoriteFeedIcon.kt` (Compose-Canvas), `FavoriteStarGeometryTest.kt` (Regressionstests).
- Version: 1.87.44
- Version Code: 173
- Keine Schema-Änderung, keine neue Abhängigkeit.

## Validierung
- `testDebugUnitTest`: 375 Tests, 0 Failures, 0 Errors, 1 übersprungen (29 Suiten).
- Auf `alice` (Redmi Note 9S) installiert und auf der Startseite gesichtet (dunkler Modus).
