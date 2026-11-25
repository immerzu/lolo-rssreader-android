package de.lolo.rssreader.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstest fuer RSS-FIX-001: Das Hauptmenue der Startseite war durch eine starre
 * 320-dp-Grenze abgeschnitten. Die Hoehe muss sich am Inhalt und am verfuegbaren
 * Bildschirmbereich orientieren.
 */
class HomeMenuHeightTest {

    // Zwoelf Menueeintraege mit einer Mindesthoehe von je 30 dp (compactMenuItem()).
    private val minimumMenuContentHeight = 30.dp * 12

    @Test
    fun tallDisplayAllowsFullMenuHeightInsteadOfFixed320Dp() {
        val maxHeight = resolveHomeMenuMaxHeight(
            availableHeight = 816.dp,
            topInset = 24.dp,
            bottomInset = 48.dp
        )

        // 816 - 24 Statusleiste - 56 Symbolleisten-Abstand - 48 Navigationsleiste - 8 Rand
        assertEquals(680.dp, maxHeight)
        assertTrue(
            "Das Menue darf nicht auf die fruehere 320-dp-Grenze begrenzt werden",
            maxHeight > 320.dp
        )
        assertTrue(
            "Alle zwoelf Menueeintraege muessen ohne Scrollen Platz haben",
            maxHeight >= minimumMenuContentHeight
        )
    }

    @Test
    fun smallDisplayKeepsMenuScrollableInsteadOfOverflowing() {
        val maxHeight = resolveHomeMenuMaxHeight(
            availableHeight = 400.dp,
            topInset = 24.dp,
            bottomInset = 48.dp
        )

        assertEquals(264.dp, maxHeight)
        assertTrue("Das Menue muss bei kleiner Anzeige sichtbar bleiben", maxHeight > 0.dp)
        assertTrue(
            "Bei kleiner Anzeige bleibt der Inhalt laenger als die Menueflaeche (Scrollen)",
            maxHeight < minimumMenuContentHeight
        )
    }

    @Test
    fun displayCutoutReducesAvailableMenuHeight() {
        val withStatusBarOnly = resolveHomeMenuMaxHeight(
            availableHeight = 816.dp,
            topInset = 24.dp,
            bottomInset = 48.dp
        )
        val withCutout = resolveHomeMenuMaxHeight(
            availableHeight = 816.dp,
            topInset = 44.dp,
            bottomInset = 48.dp
        )

        assertEquals(660.dp, withCutout)
        assertTrue(withCutout < withStatusBarOnly)
    }

    @Test
    fun menuHeightNeverBecomesNegative() {
        val maxHeight = resolveHomeMenuMaxHeight(
            availableHeight = 100.dp,
            topInset = 24.dp,
            bottomInset = 48.dp
        )

        assertEquals(0.dp, maxHeight)
    }
}
