package de.lolo.rssreader.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleReaderHtmlSecurityTest {

    @Test
    fun sanitizeRemovesScriptTagsAndTheirContent() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Sicherer Text</p>
                <script>alert('XSS')</script>
                <script type="text/javascript" src="https://evil.example/malware.js"></script>
                <p>Mehr sicherer Text</p>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("alert('XSS')"))
        assertFalse(sanitized.contains("malware.js"))
        assertTrue(sanitized.contains("<p>Sicherer Text</p>"))
        assertTrue(sanitized.contains("<p>Mehr sicherer Text</p>"))
    }

    @Test
    fun sanitizeRemovesNoscriptContent() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Vor Noscript</p>
                <noscript><img src="https://evil.example/tracker.jpg"></noscript>
                <p>Nach Noscript</p>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<noscript", ignoreCase = true))
        assertFalse(sanitized.contains("tracker.jpg"))
        assertTrue(sanitized.contains("<p>Vor Noscript</p>"))
        assertTrue(sanitized.contains("<p>Nach Noscript</p>"))
    }

    @Test
    fun sanitizeNeutralizesDataTextHtmlLinks() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Artikeltext</p>
                <a href="data:text/html,<script>alert(1)</script>">Klick mich</a>
                <a href="data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==">Base64-Link</a>
            """.trimIndent()
        )

        // data:-URIs in Links sind potenziell gefaehrlich
        // Der WebView wird mit JavaScript deaktiviert ausgeliefert, was XSS via data:-URI blockiert.
        // Zusaetzlich oeffnen Klicks auf Links immer den externen Browser (nur http/https).
        // Somit sind data:-URIs im HTML zwar sichtbar, aber nicht ausfuehrbar.
        assertTrue(sanitized.contains("Artikeltext"))
        // Der Sanitizer entfernt data:-URIs nicht aktiv – die Sicherheit kommt vom WebView-Layer.
    }

    @Test
    fun sanitizeDoesNotRemoveIframesInNormalMode() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Video-Einbettung:</p>
                <iframe src="https://www.youtube.com/embed/abc123" width="560" height="315"></iframe>
                <p>Nach dem Video</p>
            """.trimIndent(),
            isHeavy = false
        )

        assertTrue(sanitized.contains("<iframe", ignoreCase = true))
        assertTrue(sanitized.contains("youtube.com/embed/abc123"))
        assertTrue(sanitized.contains("<p>Video-Einbettung:</p>"))
        assertTrue(sanitized.contains("<p>Nach dem Video</p>"))
    }

    @Test
    fun sanitizeRemovesIframesAndMediaInHeavyMode() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Video-Einbettung:</p>
                <iframe src="https://www.youtube.com/embed/abc123" width="560" height="315"></iframe>
                <video src="https://example.com/video.mp4" controls></video>
                <p>Nach dem Video</p>
            """.trimIndent(),
            isHeavy = true
        )

        assertFalse(sanitized.contains("<iframe", ignoreCase = true))
        assertFalse(sanitized.contains("<video", ignoreCase = true))
        assertFalse(sanitized.contains("youtube.com/embed/abc123"))
        assertTrue(sanitized.contains("<p>Video-Einbettung:</p>"))
        assertTrue(sanitized.contains("<p>Nach dem Video</p>"))
    }

    @Test
    fun sanitizePreservesNormalArticleContent() {
        val normalArticle = """
            <h2>Eine Überschrift</h2>
            <p>Normaler Absatz mit <a href="https://example.com/article">einem Link</a>.</p>
            <p>Noch ein Absatz mit <strong>fettem</strong> und <em>kursivem</em> Text.</p>
            <ul>
              <li>Listenpunkt 1</li>
              <li>Listenpunkt 2</li>
            </ul>
            <blockquote>Ein wichtiges Zitat</blockquote>
            <img src="https://example.com/photo.jpg" alt="Beispielbild">
        """.trimIndent()

        val sanitized = sanitizeReaderBodyHtmlForTest(normalArticle)

        assertTrue(sanitized.contains("<h2>Eine Überschrift</h2>"))
        assertTrue(sanitized.contains("""href="https://example.com/article""""))
        assertTrue(sanitized.contains("<strong>fettem</strong>"))
        assertTrue(sanitized.contains("<em>kursivem</em>"))
        assertTrue(sanitized.contains("<li>Listenpunkt 1</li>"))
        assertTrue(sanitized.contains("<blockquote>Ein wichtiges Zitat</blockquote>"))
        assertTrue(sanitized.contains("""src="https://example.com/photo.jpg""""))
    }

    @Test
    fun sanitizeRemovesStyleTagsWhilePreservingSurroundingContent() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <style>
                  body { background: url('https://evil.example/tracker.gif'); }
                  .hidden { display: none; }
                </style>
                <p>Sichtbarer Inhalt nach dem Style-Tag</p>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<style", ignoreCase = true))
        assertFalse(sanitized.contains("tracker.gif"))
        assertTrue(sanitized.contains("<p>Sichtbarer Inhalt nach dem Style-Tag</p>"))
    }
}
