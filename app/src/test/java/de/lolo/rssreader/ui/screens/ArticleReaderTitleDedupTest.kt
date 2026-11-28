package de.lolo.rssreader.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RSS-FIX-003: Der Reader rendert den Artikeltitel selbst. Eine identische Titelzeile im
 * Artikel-HTML (typisch bei Volltextseiten) darf nicht doppelt erscheinen; abweichende
 * Ueberschriften und Zwischenueberschriften muessen erhalten bleiben.
 */
class ArticleReaderTitleDedupTest {

    private val title = "SPD-Bürgermeister Egbert Geier bleibt im Amt"

    @Test
    fun identischeTitelUeberschriftWirdEntfernt() {
        val html = "<article><h1>$title</h1><p>Absatz mit Inhalt.</p></article>"

        val result = removeDuplicateTitleHeading(html, title)

        assertFalse("Titel darf nicht erneut im Body stehen", result.contains(title))
        assertTrue("Der Artikelinhalt bleibt erhalten", result.contains("Absatz mit Inhalt."))
    }

    @Test
    fun abweichendeUeberschriftBleibtErhalten() {
        val html = "<article><h1>Andere Überschrift</h1><p>Absatz.</p></article>"

        val result = removeDuplicateTitleHeading(html, title)

        assertTrue("Abweichende Ueberschriften bleiben erhalten", result.contains("Andere Überschrift"))
    }

    @Test
    fun zwischenueberschriftenBleibenErhalten() {
        val html = "<article><h1>$title</h1><p>Einleitung</p><h2>Zwischenüberschrift</h2><p>Mehr</p></article>"

        val result = removeDuplicateTitleHeading(html, title)

        assertFalse(result.contains("<h1>$title</h1>"))
        assertTrue("Zwischenueberschrift h2 bleibt", result.contains("<h2>Zwischenüberschrift</h2>"))
    }

    @Test
    fun vergleichIgnoriertGrossschreibungUndMehrfacheLeerzeichen() {
        val html = "<article><h1>  SPD-Bürgermeister   Egbert Geier bleibt im Amt </h1><p>Text</p></article>"

        val result = removeDuplicateTitleHeading(html, title)

        assertFalse("Normalisierter Vergleich muss greifen", result.contains("Egbert Geier bleibt im Amt"))
    }

    @Test
    fun htmlOhneUeberschriftBleibtUnveraendert() {
        val html = "<article><p>Nur Text.</p></article>"

        assertEquals(html, removeDuplicateTitleHeading(html, title))
    }

    @Test
    fun leeresHtmlBleibtLeer() {
        assertEquals("", removeDuplicateTitleHeading("", title))
        assertEquals("   ", removeDuplicateTitleHeading("   ", title))
    }

    @Test
    fun zweiteGleichlautendeUeberschriftBleibtAlsInhaltStehen() {
        // Nur die erste Ueberschrift ist die Titelwiederholung; eine spaetere gleichlautende
        // Ueberschrift ist Artikelinhalt und bleibt.
        val html = "<article><h1>$title</h1><p>Mitte</p><h1>$title</h1></article>"

        val result = removeDuplicateTitleHeading(html, title)

        assertEquals(
            "Genau eine Titelzeile bleibt als Artikelinhalt stehen",
            1,
            Regex("<h1>").findAll(result).count()
        )
        assertTrue(result.contains("Mitte"))
    }
}
