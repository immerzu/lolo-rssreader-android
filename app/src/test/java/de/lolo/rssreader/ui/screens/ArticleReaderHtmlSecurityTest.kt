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

    @Test
    fun sanitizeRemovesLegacyImageFlowAttributes() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p><img src="https://example.com/photo.jpg" align="left" hspace="8" vspace="6"></p>
                <p>Text darf nicht neben dem Bild in eine schmale Spalte fliessen.</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("""src="https://example.com/photo.jpg""""))
        assertFalse(sanitized.contains("align=", ignoreCase = true))
        assertFalse(sanitized.contains("hspace=", ignoreCase = true))
        assertFalse(sanitized.contains("vspace=", ignoreCase = true))
        assertTrue(sanitized.contains("Text darf nicht neben dem Bild"))
    }

    @Test
    fun sanitizeRemovesInlineStyleWithFloatFromImg() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p><img src="https://example.com/photo.jpg" style="float: left; width: 300px; margin-right: 10px;"></p>
                <p>Text darf nicht rechts neben dem Bild in einer schmalen Spalte fliessen.</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("""src="https://example.com/photo.jpg""""))
        assertFalse(sanitized.contains("float:", ignoreCase = true))
        // Das style-Attribut wird vollstaendig vom <img>-Tag entfernt
        assertFalse(Regex("""<img[^>]*\sstyle\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
        assertTrue(sanitized.contains("Text darf nicht rechts neben dem Bild"))
    }

    @Test
    fun sanitizeRemovesFixedVideoSizeAttributes() {
        // Echtes Markup aus einem WordPress-Kommentar-Feed (wp-video-Wrapper mit
        // fester Breite): width/height am <video> liessen es aus dem Viewport ragen.
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <div class="dco-attachment dco-video-attachment">
                  <div style="width: 640px;" class="wp-video">
                    <video class="wp-video-shortcode" width="640" height="360" preload="metadata" controls="controls">
                      <source type="video/mp4" src="https://example.com/digi-id.mp4?_=1" />
                    </video>
                  </div>
                </div>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("""width="640""""))
        assertFalse(sanitized.contains("""height="360""""))
        assertTrue(sanitized.contains("<video"))
        assertTrue(sanitized.contains("controls=\"controls\""))
        assertTrue(sanitized.contains("digi-id.mp4?_=1"))
    }

    @Test
    fun sanitizePreservesAudioAndPictureMediaElements() {
        // Podcast-Audio und <picture>-Bilder muessen erhalten bleiben; das Reader-CSS
        // begrenzt und zentriert sie (universelle Medienregel).
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <audio controls="controls" src="https://example.com/podcast.mp3"></audio>
                <picture>
                    <source srcset="https://example.com/photo.webp" type="image/webp">
                    <img src="https://example.com/photo.jpg" alt="">
                </picture>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<audio"))
        assertTrue(sanitized.contains("podcast.mp3"))
        assertTrue(sanitized.contains("<picture"))
        assertTrue(sanitized.contains("photo.jpg"))
    }

    @Test
    fun sanitizeRemovesInlineStyleWithFloatFromFigure() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <figure style="float: left; width: 50%;">
                    <img src="https://example.com/photo.jpg">
                </figure>
                <p>Text darf nicht rechts neben der figure-Box fliessen.</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("""src="https://example.com/photo.jpg""""))
        assertTrue(sanitized.contains("<figure"))
        assertFalse(Regex("""<figure[^>]*\sstyle\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
        assertTrue(sanitized.contains("Text darf nicht rechts neben der figure-Box"))
    }

    // Regression RSS-A01: Ein exakt erwartetes "</script>" liess sich mit gueltigem
    // HTML umgehen; in Chromium wurde das Skript dadurch ausgefuehrt.
    @Test
    fun sanitizeRemovesScriptTagWithWhitespaceBeforeEndTag() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            "<p>Text</p><script>document.body.dataset.audit=456</script ><p>Danach</p>"
        )

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("dataset.audit=456"))
        assertTrue(sanitized.contains("<p>Text</p>"))
        assertTrue(sanitized.contains("<p>Danach</p>"))
    }

    @Test
    fun sanitizeRemovesScriptTagWithAttributesInEndTag() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            "<p>Text</p><script type=\"text/javascript\">alert(1)</script foo=\"bar\"><p>Danach</p>"
        )

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("alert(1)"))
        assertTrue(sanitized.contains("<p>Danach</p>"))
    }

    @Test
    fun sanitizeRemovesSelfClosingScriptTag() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            "<p>Text</p><script src=\"https://evil.example/x.js\"/><p>Danach</p>"
        )

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("evil.example"))
        assertTrue(sanitized.contains("<p>Danach</p>"))
    }

    // Regression RSS-A01: <iframe srcdoc> fuehrt im Eltern-Dokument Skripte aus.
    @Test
    fun sanitizeRemovesIframeSrcdocPayload() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            "<p>Video</p><iframe srcdoc=\"&lt;script&gt;parent.document.body.dataset.audit=123&lt;/script&gt;\"></iframe><p>Danach</p>"
        )

        assertFalse(sanitized.contains("srcdoc", ignoreCase = true))
        assertFalse(sanitized.contains("dataset.audit"))
        assertTrue(sanitized.contains("<p>Danach</p>"))
    }

    // Regression RSS-A01: javascript:-URLs sind ausfuehrbar, sobald JavaScript aktiv ist.
    @Test
    fun sanitizeRemovesExecutableUrlAttributes() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Text</p>
                <a href="javascript:alert(1)">Klick</a>
                <img src="vbscript:msgbox(1)">
            """.trimIndent()
        )

        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertFalse(sanitized.contains("vbscript:", ignoreCase = true))
        assertTrue(sanitized.contains("<p>Text</p>"))
    }

    // Regression RSS-A08: "display: none" verhindert keinen Bildabruf.
    @Test
    fun sanitizeRemovesImagesWhenImageDisplayIsDisabled() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Text</p>
                <img src="https://example.com/tracker.jpg">
                <picture>
                    <source srcset="https://example.com/photo.webp" type="image/webp">
                    <img src="https://example.com/photo.jpg" alt="">
                </picture>
            """.trimIndent(),
            showImages = false
        )

        assertFalse(sanitized.contains("tracker.jpg"))
        assertFalse(sanitized.contains("photo.jpg"))
        assertFalse(sanitized.contains("photo.webp"))
        assertFalse(sanitized.contains("<img", ignoreCase = true))
        assertTrue(sanitized.contains("<p>Text</p>"))
    }

    @Test
    fun sanitizeKeepsImagesWhenImageDisplayIsEnabled() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            "<p>Text</p><img src=\"https://example.com/photo.jpg\">",
            showImages = true
        )

        assertTrue(sanitized.contains("photo.jpg"))
    }
}
