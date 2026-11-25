package de.lolo.rssreader.ui.screens

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstest fuer RSS-FIX-002: Das Hauptmenue der Startseite hat ein leicht
 * abgerundetes Fenster (6 dp an allen vier Ecken), ohne die Geometrie zu veraendern.
 */
class HomeMenuShapeTest {

    @Test
    fun menuShapeRoundsAllFourCornersWithSixDp() {
        val shape = HOME_MENU_SHAPE as RoundedCornerShape
        val expectedCorner = CornerSize(6.dp)

        assertEquals("oben links", expectedCorner, shape.topStart)
        assertEquals("oben rechts", expectedCorner, shape.topEnd)
        assertEquals("unten links", expectedCorner, shape.bottomStart)
        assertEquals("unten rechts", expectedCorner, shape.bottomEnd)
    }

    @Test
    fun menuCornerRadiusStaysSubtle() {
        assertTrue("Ecken muessen gerundet sein", HOME_MENU_CORNER_RADIUS > 0.dp)
        assertTrue(
            "Die Rundung muss dezent bleiben (hoechstens 8 dp)",
            HOME_MENU_CORNER_RADIUS <= 8.dp
        )
    }
}
