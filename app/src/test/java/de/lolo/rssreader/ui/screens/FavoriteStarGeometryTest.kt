package de.lolo.rssreader.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstests fuer die Geometrie des Favoriten-Icons.
 *
 * Ohne Korrektur stand das "F" in der Mitte der Bounding-Box (y = 0.5) und damit
 * sichtbar zu hoch im Stern; der Flaechenschwerpunkt liegt bei y = 0.5528.
 */
class FavoriteStarGeometryTest {

    @Test
    fun sternFuelltDieIconKanteInBeidenAchsen() {
        val points = FavoriteStarGeometry.points
        assertEquals(10, points.size)
        assertEquals(0.0, points.minOf { it.x }, 1e-9)
        assertEquals(1.0, points.maxOf { it.x }, 1e-9)
        assertEquals(0.0, points.minOf { it.y }, 1e-9)
        assertEquals(1.0, points.maxOf { it.y }, 1e-9)
    }

    @Test
    fun buchstabeSitztImFlaechenschwerpunktNichtInDerBoxMitte() {
        val centroid = FavoriteStarGeometry.centroid
        assertTrue(
            "Der Flaechenschwerpunkt muss unterhalb der Box-Mitte liegen (obere Spitze ist laenger)",
            centroid.y > 0.5
        )
        assertEquals(1.0 / 1.809017, centroid.y, 5e-4)
        assertEquals(0.5, centroid.x, 1e-9)
    }
}
