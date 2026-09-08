package de.lolo.rssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleFullTextExtractorTest {

    private fun longText(repetitions: Int = 6): String =
        (1..repetitions).joinToString(" ") {
            "Dies ist ein ausreichend langer Absatz fuer die Volltextextraktion im Reader."
        }

    @Test
    fun extractsArticleContainerAndIgnoresNavigationAndScripts() {
        val html = """
            <html><head><title>Seitentitel</title>
            <script>window.alert('nicht extrahieren')</script>
            <style>.x { color: red; }</style></head>
            <body>
              <nav>Navigation Startseite Impressum</nav>
              <header>Kopfzeile der Webseite</header>
              <article>
                <h1>Der Artikel</h1>
                <p>${longText()}</p>
              </article>
              <footer>Fusszeile mit Copyright</footer>
            </body></html>
        """.trimIndent()

        val extracted = extractArticleContentFromHtml(html, "https://example.com/artikel")

        assertNotNull(extracted)
        assertEquals("Der Artikel", extracted!!.title)
        assertTrue(extracted.plainText.contains("ausreichend langer Absatz"))
        assertTrue(!extracted.plainText.contains("Navigation"))
        assertTrue(!extracted.plainText.contains("Fusszeile"))
        assertTrue(!extracted.html.contains("<script"))
    }

    @Test
    fun fallsBackToBodyWhenNoSpecificContainerExists() {
        val html = "<html><body><p>${longText()}</p></body></html>"

        val extracted = extractArticleContentFromHtml(html, "https://example.com/a")

        assertNotNull(extracted)
        assertTrue(extracted!!.plainText.contains("ausreichend langer Absatz"))
    }

    @Test
    fun returnsNullWhenTextIsTooShort() {
        val html = "<html><body><article><p>Zu kurz.</p></article></body></html>"

        assertNull(extractArticleContentFromHtml(html, "https://example.com/a"))
    }

    @Test
    fun returnsNullForBlankHtml() {
        assertNull(extractArticleContentFromHtml("   ", "https://example.com/a"))
    }

    @Test
    fun resolvesRelativeLinksAndImagesToAbsoluteUrls() {
        val html = """
            <html><body><article>
              <p>${longText()}</p>
              <a href="/relativ">Link</a>
              <img src="/bilder/foto.jpg">
              <img src="https://cdn.example.com/zweit.jpg">
            </article></body></html>
        """.trimIndent()

        val extracted = extractArticleContentFromHtml(html, "https://example.com/artikel/1")

        assertNotNull(extracted)
        assertTrue(extracted!!.html.contains("https://example.com/relativ"))
        assertEquals(
            listOf("https://example.com/bilder/foto.jpg", "https://cdn.example.com/zweit.jpg"),
            extracted.imageUrls
        )
    }

    @Test
    fun removesInlineEventHandlersAndJavascriptLinks() {
        val html = """
            <html><body><article onclick="alert(1)">
              <p>${longText()}</p>
              <a href="javascript:alert(2)">boese</a>
              <img src="javascript:alert(3)">
            </article></body></html>
        """.trimIndent()

        val extracted = extractArticleContentFromHtml(html, "https://example.com/a")

        assertNotNull(extracted)
        assertTrue(!extracted!!.html.contains("onclick"))
        assertTrue(!extracted.html.contains("javascript:"))
        assertTrue(extracted.imageUrls.isEmpty())
    }

    @Test
    fun deduplicatesImageUrls() {
        val html = """
            <html><body><article>
              <p>${longText()}</p>
              <img src="https://cdn.example.com/a.jpg">
              <img src="https://cdn.example.com/a.jpg">
            </article></body></html>
        """.trimIndent()

        val extracted = extractArticleContentFromHtml(html, "https://example.com/a")

        assertNotNull(extracted)
        assertEquals(listOf("https://cdn.example.com/a.jpg"), extracted!!.imageUrls)
    }

    @Test
    fun prefersArticleOverLargeBodyWrapper() {
        val html = """
            <html><body>
              <div class="sidebar">${"Werbung und Navigation. ".repeat(30)}</div>
              <article><p>${longText()}</p></article>
            </body></html>
        """.trimIndent()

        val extracted = extractArticleContentFromHtml(html, "https://example.com/a")

        assertNotNull(extracted)
        assertTrue(extracted!!.plainText.contains("ausreichend langer Absatz"))
        assertTrue(!extracted.plainText.contains("Werbung und Navigation"))
    }
}
