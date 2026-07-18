package de.lolo.rssreader.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regressionstests fuer die Erzeugung des JavaScript-String-Literals, in das
 * der (nicht vertrauenswuerdige) `articleTapUrl`-Wert im Reader-WebView-Skript
 * eingebettet wird (frueher: `var articleUrl = '$articleTapUrl';`).
 *
 * Die Tests belegen fuer jeden sicherheitsrelevanten Sonderfall:
 *  1. Das Ergebnis ist EIN einzelnes, korrekt gequotetes JS-String-Literal
 *     (beginnt/endet mit `"`, keine vorzeitige Terminierung durch unescapete `"`).
 *  2. Der eingebettete Wert bleibt semantisch unveraendert (Round-Trip-Decode
 *     ergibt exakt die Eingabe).
 *
 * Der Decoder ist bewusst unabhaengig von der Produktions-Implementierung
 * (JSONObject.quote) nachgebaut, damit die Semantik-Erhaltung wirklich geprueft
 * und nicht nur die Encoder-Eigenimplementierung gespiegelt wird.
 */
@RunWith(RobolectricTestRunner::class)
class ArticleReaderJavaScriptInjectionTest {

    @Test
    fun singleQuoteStaysInsideOneLiteral() {
        assertRoundTripAndSingleLiteral("https://example.com/it's-a-test")
    }

    @Test
    fun doubleQuoteIsEscapedAndDoesNotTerminateLiteral() {
        assertRoundTripAndSingleLiteral("https://example.com/say-\"hi\"")
    }

    @Test
    fun backslashIsEscaped() {
        assertRoundTripAndSingleLiteral("https://example.com/a\\b\\c")
    }

    @Test
    fun newlineAndCarriageReturnAreEscaped() {
        val value = "https://example.com/line1\r\nline2"
        val literal = toJavaScriptStringLiteral(value)

        // Ein rohes Zeilenende wuerde ein JS-String-Literal ungueltig machen /
        // aufbrechen. Es darf im Ergebnis kein rohes \n oder \r vorkommen.
        assertFalse("Rohes Newline darf nicht im Literal stehen", literal.contains('\n'))
        assertFalse("Rohes CarriageReturn darf nicht im Literal stehen", literal.contains('\r'))
        assertRoundTripAndSingleLiteral(value)
    }

    @Test
    fun unicodeLineSeparatorU2028StaysWithinLiteral() {
        // U+2028 ist in ES2019+ innerhalb von String-Literalen gueltig und wird
        // unveraendert durchgereicht; der Round-Trip muss ihn exakt erhalten.
        assertRoundTripAndSingleLiteral("https://example.com/a\u2028b")
    }

    @Test
    fun unicodeParagraphSeparatorU2029StaysWithinLiteral() {
        assertRoundTripAndSingleLiteral("https://example.com/a\u2029b")
    }

    @Test
    fun javascriptBreakoutAttemptIsNeutralized() {
        // Klassischer Ausbruchsversuch: haette bei naiver Einbettung
        // (`var articleUrl = '...';`) das String-Literal beendet und Code
        // eingeschleust.
        val payload = "rssreader-article://open?url=x'; alert(document.cookie); //"
        val literal = toJavaScriptStringLiteral(payload)
        val statement = "var articleUrl = $literal;"

        // Das gesamte Statement enthaelt genau EIN String-Literal; der Payload
        // steckt vollstaendig darin und wird beim Decodieren unveraendert
        // zurueckgegeben -> kein Ausbruch moeglich.
        assertRoundTripAndSingleLiteral(payload)

        // Zusaetzliche Absicherung: nach dem schliessenden Anfuehrungszeichen des
        // Literals folgt ausschliesslich das Semikolon des Statements.
        val closingQuoteIndex = statement.lastIndexOf('"')
        assertEquals(
            "Nach dem Literal darf nur das Statement-Semikolon folgen",
            ";",
            statement.substring(closingQuoteIndex + 1)
        )
    }

    @Test
    fun normalHttpsLinkIsPreservedAsQuotedLiteral() {
        // Positivfall: eine gewoehnliche gueltige URL bleibt unveraendert und
        // wird als sauberes, in Anfuehrungszeichen gefasstes Literal ausgegeben.
        val value = "rssreader-article://open?url=https%3A%2F%2Fexample.com%2Farticle%2F42"
        val literal = toJavaScriptStringLiteral(value)

        assertTrue("Literal beginnt mit doppeltem Anfuehrungszeichen", literal.startsWith("\""))
        assertTrue("Literal endet mit doppeltem Anfuehrungszeichen", literal.endsWith("\""))
        assertRoundTripAndSingleLiteral(value)
    }

    // --- Vollständiger Produktionspfad --------------------------------------
    // article.link -> Uri.encode(...) -> articleTapUrl -> JS-Quoting ->
    // tatsächliche Zuweisungszeile (buildArticleUrlAssignment).

    @Test
    fun fullProductionPathNeutralizesUriEncodeSurvivingBreakoutPayload() {
        // article.link (nicht vertrauenswuerdig). Die fuer den Ausbruch noetigen
        // Zeichen ' * ( ) gehoeren zur Default-Allow-Menge von Uri.encode und
        // werden daher NICHT prozentkodiert -> sie erreichen die Einbettung.
        val link = "x'*alert(1)*'y"
        val articleTapUrl = buildArticleTapUrl(link)

        // Nachweis: das einfache Anfuehrungszeichen ueberlebt Uri.encode.
        assertTrue(
            "Uri.encode muss das einfache Anfuehrungszeichen unveraendert lassen",
            articleTapUrl.contains('\'')
        )
        assertTrue(
            "Erwarteter articleTapUrl-Aufbau",
            articleTapUrl == "rssreader-article://open?url=x'*alert(1)*'y"
        )

        // Nachweis: die fruehere naive Einbettung (einfach gequotet) waere
        // syntaktisch aufgebrochen -> mehr als die zwei umschliessenden Quotes.
        val naiveVulnerableEmbedding = "var articleUrl = '$articleTapUrl';"
        assertTrue(
            "Naive einfach-gequotete Einbettung wuerde aufbrechen",
            naiveVulnerableEmbedding.count { it == '\'' } > 2
        )

        // Korrigierter Produktionspfad: exakt EIN Literal, korrekt gequotet.
        assertProductionAssignmentWrapsSingleLiteral(articleTapUrl)

        // Der dekodierte Wert enthaelt den vollstaendigen Payload unveraendert.
        val literal = buildArticleUrlAssignment(articleTapUrl)
            .removePrefix("var articleUrl = ")
            .removeSuffix(";")
        assertTrue(
            "Der vollstaendige Payload bleibt Bestandteil des einen Literals",
            decodeJsStringLiteral(literal).contains("x'*alert(1)*'y")
        )
    }

    @Test
    fun fullProductionPathPreservesNormalHttpsLink() {
        val link = "https://example.com/article/42"
        val articleTapUrl = buildArticleTapUrl(link)

        // Uri.encode kodiert : und / -> normaler, gueltiger articleTapUrl.
        assertEquals(
            "rssreader-article://open?url=https%3A%2F%2Fexample.com%2Farticle%2F42",
            articleTapUrl
        )
        assertProductionAssignmentWrapsSingleLiteral(articleTapUrl)
    }

    @Test
    fun buildArticleTapUrlReturnsEmptyForBlankLink() {
        // Randfall: bei leerem Link entsteht kein Skript-Zuweisungswert.
        assertEquals("", buildArticleTapUrl(""))
        assertEquals("", buildArticleTapUrl("   "))
    }

    // --- Hilfsfunktionen -----------------------------------------------------

    /**
     * Prueft die tatsaechlich verwendete Zuweisungszeile aus dem Produktionscode
     * (`buildArticleUrlAssignment`): genau ein korrekt gequotetes Literal, ohne
     * zusaetzliche manuelle Quotierung, semantisch identisch zu [articleTapUrl].
     */
    private fun assertProductionAssignmentWrapsSingleLiteral(articleTapUrl: String) {
        val assignment = buildArticleUrlAssignment(articleTapUrl)

        // Das Literal ist direkt in doppelte Anfuehrungszeichen gefasst; es gibt
        // keine umschliessenden einfachen oder verdoppelten Anfuehrungszeichen.
        assertTrue(
            "Zuweisung muss direkt mit doppelt gequotetem Literal beginnen: $assignment",
            assignment.startsWith("var articleUrl = \"")
        )
        assertTrue(
            "Zuweisung muss mit \"; enden: $assignment",
            assignment.endsWith("\";")
        )
        assertFalse(
            "Keine manuelle einfache Quotierung um das Literal",
            assignment.contains("var articleUrl = '") || assignment.endsWith("';")
        )

        val literal = assignment.removePrefix("var articleUrl = ").removeSuffix(";")

        // Es steckt genau die Ausgabe EINES Quote-Aufrufs im Statement – keine
        // zusaetzlichen Zeichen davor oder danach.
        assertEquals(
            "Es darf ausschliesslich das gequotete Literal eingebettet sein",
            toJavaScriptStringLiteral(articleTapUrl),
            literal
        )

        // Genau ein zusammenhaengendes Literal + Round-Trip auf den Originalwert.
        assertEquals(
            "Das dekodierte Literal muss exakt dem articleTapUrl entsprechen",
            articleTapUrl,
            decodeJsStringLiteral(literal)
        )
    }

    private fun assertRoundTripAndSingleLiteral(value: String) {
        val literal = toJavaScriptStringLiteral(value)

        assertTrue(
            "Literal muss mit doppeltem Anfuehrungszeichen beginnen: $literal",
            literal.startsWith("\"")
        )
        assertTrue(
            "Literal muss mit doppeltem Anfuehrungszeichen enden: $literal",
            literal.endsWith("\"") && literal.length >= 2
        )

        val decoded = decodeJsStringLiteral(literal)
        assertEquals(
            "Der eingebettete Wert muss semantisch unveraendert bleiben",
            value,
            decoded
        )
    }

    /**
     * Minimaler, eigenstaendiger Decoder fuer ein JS/JSON-String-Literal. Er
     * verlangt, dass das Literal genau EIN zusammenhaengendes, in doppelten
     * Anfuehrungszeichen gefasstes Literal ist. Eine unescapete `"` vor dem Ende
     * (= vorzeitige Terminierung / Ausbruch) fuehrt zu einer Assertion-Failure.
     */
    private fun decodeJsStringLiteral(literal: String): String {
        assertTrue("Literal zu kurz", literal.length >= 2)
        assertEquals('"', literal.first())
        assertEquals('"', literal.last())

        val body = literal.substring(1, literal.length - 1)
        val sb = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '"') {
                throw AssertionError(
                    "Unescapetes Anfuehrungszeichen im Literal-Rumpf: vorzeitige Terminierung"
                )
            }
            if (c != '\\') {
                sb.append(c)
                i++
                continue
            }
            // Escape-Sequenz
            i++
            assertTrue("Unvollstaendige Escape-Sequenz", i < body.length)
            when (val esc = body[i]) {
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'u' -> {
                    assertTrue("Unvollstaendige \\u-Sequenz", i + 4 < body.length)
                    val hex = body.substring(i + 1, i + 5)
                    sb.append(hex.toInt(16).toChar())
                    i += 4
                }
                else -> throw AssertionError("Unbekannte Escape-Sequenz: \\$esc")
            }
            i++
        }
        return sb.toString()
    }
}
