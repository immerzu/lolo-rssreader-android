package de.lolo.rssreader.ui.screens

import de.lolo.rssreader.data.db.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleReaderScreenBaseUrlTest {

    @Test
    fun determineHorizontalSwipeDirectionDetectsNewerSwipe() {
        assertEquals(
            SwipeDirection.ToNewer,
            determineHorizontalSwipeDirection(
                totalHorizontalDrag = 140f,
                totalVerticalDrag = 10f,
                canGoNewer = true,
                canGoOlder = true
            )
        )
    }

    @Test
    fun determineHorizontalSwipeDirectionDetectsOlderSwipe() {
        assertEquals(
            SwipeDirection.ToOlder,
            determineHorizontalSwipeDirection(
                totalHorizontalDrag = -140f,
                totalVerticalDrag = 10f,
                canGoNewer = true,
                canGoOlder = true
            )
        )
    }

    @Test
    fun determineHorizontalSwipeDirectionIgnoresNonDominantSwipe() {
        assertEquals(
            SwipeDirection.None,
            determineHorizontalSwipeDirection(
                totalHorizontalDrag = 100f,
                totalVerticalDrag = 100f,
                canGoNewer = true,
                canGoOlder = true
            )
        )
    }

    @Test
    fun determineHorizontalSwipeDirectionIgnoresDiagonalTextSelection() {
        // Diagonale Bewegung wie beim Textmarkieren: horizontal dominant, aber
        // nicht genug, um den 1.75f-Bias zu ueberwinden.
        assertEquals(
            SwipeDirection.None,
            determineHorizontalSwipeDirection(
                totalHorizontalDrag = 130f,
                totalVerticalDrag = 80f,
                canGoNewer = true,
                canGoOlder = true
            )
        )
    }

    @Test
    fun determineHorizontalSwipeDirectionRequiresMinimumDistance() {
        // Unterhalb der 120px-Schwelle kein Swipe.
        assertEquals(
            SwipeDirection.None,
            determineHorizontalSwipeDirection(
                totalHorizontalDrag = 100f,
                totalVerticalDrag = 0f,
                canGoNewer = true,
                canGoOlder = true
            )
        )
    }

    @Test
    fun resolveReaderWebViewBaseUrlUsesHttpArticleUrl() {
        assertEquals(
            "https://dasgelbeforum.net/index.php?id=683167",
            resolveReaderWebViewBaseUrl("https://dasgelbeforum.net/index.php?id=683167")
        )
    }

    @Test
    fun resolveReaderWebViewBaseUrlTrimsArticleUrl() {
        assertEquals(
            "https://dasgelbeforum.net/index.php?id=683167",
            resolveReaderWebViewBaseUrl("  https://dasgelbeforum.net/index.php?id=683167  ")
        )
    }

    @Test
    fun resolveReaderWebViewBaseUrlFallsBackForBlankOrNonHttpUrl() {
        assertEquals(
            "https://localhost/",
            resolveReaderWebViewBaseUrl(null)
        )
        assertEquals(
            "https://localhost/",
            resolveReaderWebViewBaseUrl("   ")
        )
        assertEquals(
            "https://localhost/",
            resolveReaderWebViewBaseUrl("content://example/article")
        )
    }

    @Test
    fun shouldBlockReaderSubresourceBlocksLegacyActiveSchemesButNotMainFrame() {
        assertTrue(shouldBlockReaderSubresourceScheme("file", isMainFrame = false))
        assertTrue(shouldBlockReaderSubresourceScheme("content", isMainFrame = false))
        assertTrue(shouldBlockReaderSubresourceScheme("intent", isMainFrame = false))
        assertTrue(shouldBlockReaderSubresourceScheme("vbscript", isMainFrame = false))
        assertTrue(shouldBlockReaderSubresourceScheme("javascript", isMainFrame = false))
        assertFalse(shouldBlockReaderSubresourceScheme("vbscript", isMainFrame = true))
        assertFalse(shouldBlockReaderSubresourceScheme("https", isMainFrame = false))
    }

    @Test
    fun isReaderExternallyOpenableSchemeAllowsHttpHttpsAndMailto() {
        assertTrue(isReaderExternallyOpenableScheme("http"))
        assertTrue(isReaderExternallyOpenableScheme("https"))
        assertTrue(isReaderExternallyOpenableScheme("mailto"))
        assertFalse(isReaderExternallyOpenableScheme("intent"))
        assertFalse(isReaderExternallyOpenableScheme("javascript"))
        assertFalse(isReaderExternallyOpenableScheme("content"))
    }

    @Test
    fun isReaderExternallyOpenableSchemeAllowsHttpsUrlWithFragment() {
        // Fragment beeinflusst die Scheme-Erkennung nicht – https bleibt https.
        assertTrue(isReaderExternallyOpenableScheme("https"))
    }

    @Test
    fun resolveReaderFallbackBodyTextUsesStoredPlainTextWhenPresent() {
        assertEquals(
            "Originaltext ohne Uebersetzung",
            resolveReaderFallbackBodyText(
                article(plainText = "Originaltext ohne Uebersetzung")
            )
        )
    }

    @Test
    fun resolveReaderFallbackBodyTextUsesPlaceholderWhenStoredTextIsBlank() {
        assertEquals(
            "Kein Textinhalt vorhanden.",
            resolveReaderFallbackBodyText(
                article(plainText = "   ")
            )
        )
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesInlineEventHandlersButKeepsSafeAttributes() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <div onclick="alert(1)" background="https://evil.example/bg.jpg" manifest="https://evil.example/appcache" profile="https://evil.example/profile" cite="https://evil.example/source" codebase="https://evil.example/base/" classid="clsid:1234" pluginspage="https://evil.example/plugin" contenteditable="true" spellcheck="true" tabindex="5" hidefocus="true" unselectable="on" contextmenu="reader-menu" inputmode="numeric" enterkeyhint="search" autocapitalize="characters" autocorrect="on" autocomplete="on" translate="yes" popover="auto" popovertarget="reader-pop" popovertargetaction="toggle" virtualkeyboardpolicy="manual" writingsuggestions="false" itemscope itemtype="https://schema.org/Article" itemid="urn:reader:1" itemref="meta-1" itemprop="articleBody" about="https://example.com/articles/1" typeof="schema:Article" property="schema:articleBody" resource="urn:reader:resource" vocab="https://schema.org/" prefix="schema: https://schema.org/" slot="headline" part="body lead" exportparts="body:reader-body" nonce="reader-nonce" blocking="render" crossorigin="anonymous" referrerpolicy="unsafe-url" integrity="sha256-evil" attributionsrc="https://evil.example/attr" importance="high" role="article" aria-label="Gefaehrlicher Artikel" aria-labelledby="reader-title" aria-describedby="reader-summary" aria-hidden="false" style="behavior:url(#default#time2)">
                  <img src="https://example.com/image.jpg" dynsrc="https://evil.example/video.avi" lowsrc="https://evil.example/preview.jpg" datasrc="#feed" datafld="title" dataformatas="html" longdesc="https://evil.example/desc" archive="https://evil.example/archive.zip" pluginurl="https://evil.example/plugin.bin" usemap="#hotspots" ismap onerror="steal()" alt="Beispiel" style="width:100%" draggable="true">
                <a href="https://example.com/article" ping="https://evil.example/ping" target="_blank" download="reader.html" onmouseover="track()" style="background:expression(alert(1))" autofocus accesskey="k">Zum Artikel</a>
                  <span style="background-image:url(javascript:alert(1))">Gefaehrlicher Stil</span>
                  <span style="background-image:url('vbscript:msgbox(1)')">Noch ein gefaehrlicher Stil</span>
                </div>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("onclick=", ignoreCase = true))
        assertFalse(sanitized.contains("onerror=", ignoreCase = true))
        assertFalse(sanitized.contains("onmouseover=", ignoreCase = true))
        assertFalse(sanitized.contains("behavior:url", ignoreCase = true))
        assertFalse(sanitized.contains("expression(alert", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:alert", ignoreCase = true))
        assertFalse(sanitized.contains("vbscript:msgbox", ignoreCase = true))
        assertFalse(sanitized.contains("background=", ignoreCase = true))
        assertFalse(sanitized.contains("manifest=", ignoreCase = true))
        assertFalse(sanitized.contains("profile=", ignoreCase = true))
        assertFalse(sanitized.contains("cite=", ignoreCase = true))
        assertFalse(sanitized.contains("codebase=", ignoreCase = true))
        assertFalse(sanitized.contains("classid=", ignoreCase = true))
        assertFalse(sanitized.contains("pluginspage=", ignoreCase = true))
        assertFalse(sanitized.contains("contenteditable=", ignoreCase = true))
        assertFalse(sanitized.contains("spellcheck=", ignoreCase = true))
        assertFalse(sanitized.contains("tabindex=", ignoreCase = true))
        assertFalse(sanitized.contains("hidefocus=", ignoreCase = true))
        assertFalse(sanitized.contains("unselectable=", ignoreCase = true))
        assertFalse(sanitized.contains("contextmenu=", ignoreCase = true))
        assertFalse(sanitized.contains("inputmode=", ignoreCase = true))
        assertFalse(sanitized.contains("enterkeyhint=", ignoreCase = true))
        assertFalse(sanitized.contains("autocapitalize=", ignoreCase = true))
        assertFalse(sanitized.contains("autocorrect=", ignoreCase = true))
        assertFalse(sanitized.contains("autocomplete=", ignoreCase = true))
        assertFalse(sanitized.contains("translate=", ignoreCase = true))
        assertFalse(sanitized.contains("popover=", ignoreCase = true))
        assertFalse(sanitized.contains("popovertarget=", ignoreCase = true))
        assertFalse(sanitized.contains("popovertargetaction=", ignoreCase = true))
        assertFalse(sanitized.contains("virtualkeyboardpolicy=", ignoreCase = true))
        assertFalse(sanitized.contains("writingsuggestions=", ignoreCase = true))
        assertFalse(sanitized.contains("itemscope", ignoreCase = true))
        assertFalse(sanitized.contains("itemtype=", ignoreCase = true))
        assertFalse(sanitized.contains("itemid=", ignoreCase = true))
        assertFalse(sanitized.contains("itemref=", ignoreCase = true))
        assertFalse(sanitized.contains("itemprop=", ignoreCase = true))
        assertFalse(sanitized.contains("about=", ignoreCase = true))
        assertFalse(sanitized.contains("typeof=", ignoreCase = true))
        assertFalse(sanitized.contains("property=", ignoreCase = true))
        assertFalse(sanitized.contains("resource=", ignoreCase = true))
        assertFalse(sanitized.contains("vocab=", ignoreCase = true))
        assertFalse(sanitized.contains("prefix=", ignoreCase = true))
        assertFalse(sanitized.contains("slot=", ignoreCase = true))
        assertFalse(sanitized.contains("part=", ignoreCase = true))
        assertFalse(sanitized.contains("exportparts=", ignoreCase = true))
        assertFalse(sanitized.contains("nonce=", ignoreCase = true))
        assertFalse(sanitized.contains("blocking=", ignoreCase = true))
        assertFalse(sanitized.contains("crossorigin=", ignoreCase = true))
        assertFalse(sanitized.contains("referrerpolicy=", ignoreCase = true))
        assertFalse(sanitized.contains("integrity=", ignoreCase = true))
        assertFalse(sanitized.contains("attributionsrc=", ignoreCase = true))
        assertFalse(sanitized.contains("importance=", ignoreCase = true))
        assertFalse(sanitized.contains("role=", ignoreCase = true))
        assertFalse(sanitized.contains("aria-label=", ignoreCase = true))
        assertFalse(sanitized.contains("aria-labelledby=", ignoreCase = true))
        assertFalse(sanitized.contains("aria-describedby=", ignoreCase = true))
        assertFalse(sanitized.contains("aria-hidden=", ignoreCase = true))
        assertFalse(sanitized.contains("dynsrc=", ignoreCase = true))
        assertFalse(sanitized.contains("lowsrc=", ignoreCase = true))
        assertFalse(sanitized.contains("usemap=", ignoreCase = true))
        assertFalse(sanitized.contains("ismap", ignoreCase = true))
        assertFalse(sanitized.contains("draggable=", ignoreCase = true))
        assertFalse(sanitized.contains("datasrc=", ignoreCase = true))
        assertFalse(sanitized.contains("datafld=", ignoreCase = true))
        assertFalse(sanitized.contains("dataformatas=", ignoreCase = true))
        assertFalse(sanitized.contains("longdesc=", ignoreCase = true))
        assertFalse(sanitized.contains("archive=", ignoreCase = true))
        assertFalse(sanitized.contains("pluginurl=", ignoreCase = true))
        assertFalse(sanitized.contains("ping=", ignoreCase = true))
        assertFalse(Regex("""\starget\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
        assertFalse(Regex("""\sdownload\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
        assertFalse(sanitized.contains("autofocus", ignoreCase = true))
        assertFalse(sanitized.contains("accesskey=", ignoreCase = true))
        assertTrue(sanitized.contains("""src="https://example.com/image.jpg""""))
        assertTrue(sanitized.contains("""href="https://example.com/article""""))
        assertTrue(sanitized.contains("""style="width:100%""""))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesInjectedBaseTags() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <base href="https://evil.example/">
                <p>Artikeltext</p>
                <a href="/lokal">Weiter</a>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<base", ignoreCase = true))
        assertTrue(sanitized.contains("<p>Artikeltext</p>"))
        assertTrue(sanitized.contains("""href="/lokal""""))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesMetaTagsButKeepsOtherMarkup() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <meta http-equiv="refresh" content="0;url=https://evil.example/">
                <meta charset="utf-8">
                <p>Lesbarer Inhalt</p>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<meta", ignoreCase = true))
        assertTrue(sanitized.contains("<p>Lesbarer Inhalt</p>"))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesLinkTagsButKeepsBodyContent() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <link rel="preload" href="https://evil.example/asset.js">
                <p>Artikel bleibt sichtbar</p>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<link", ignoreCase = true))
        assertTrue(sanitized.contains("<p>Artikel bleibt sichtbar</p>"))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesFormTagsButKeepsInnerContent() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <form action="https://evil.example/post">
                  <p>Kommentarfeld</p>
                  <input type="text" value="Test">
                </form>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("<form", ignoreCase = true))
        assertFalse(sanitized.contains("</form", ignoreCase = true))
        assertTrue(sanitized.contains("<p>Kommentarfeld</p>"))
        assertFalse(sanitized.contains("<input", ignoreCase = true))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesStandaloneFormControls() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Vor dem Formular</p>
                <input type="text" value="Test">
                <button type="button">Klick</button>
                <select><option>Eins</option></select>
                <textarea>Notiz</textarea>
                <keygen name="legacy-key">
                <menuitem label="Altmenue">
                <command type="command" label="Altbefehl">
                <p>Nach dem Formular</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<p>Vor dem Formular</p>"))
        assertTrue(sanitized.contains("<p>Nach dem Formular</p>"))
        assertFalse(sanitized.contains("<input", ignoreCase = true))
        assertFalse(sanitized.contains("<button", ignoreCase = true))
        assertFalse(sanitized.contains("<select", ignoreCase = true))
        assertFalse(sanitized.contains("<option", ignoreCase = true))
        assertFalse(sanitized.contains("<textarea", ignoreCase = true))
        assertFalse(sanitized.contains("<keygen", ignoreCase = true))
        assertFalse(sanitized.contains("<menuitem", ignoreCase = true))
        assertFalse(sanitized.contains("<command", ignoreCase = true))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesTemplateBlocks() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Sichtbar</p>
                <template><img src="https://evil.example/hidden.jpg"></template>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<p>Sichtbar</p>"))
        assertFalse(sanitized.contains("<template", ignoreCase = true))
        assertFalse(sanitized.contains("hidden.jpg"))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesPluginEmbeds() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Vor dem Embed</p>
                <object data="https://evil.example/movie.swf"><p>Unsichtbare Reserve</p></object>
                <embed src="https://evil.example/plugin.swf">
                <p>Nach dem Embed</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<p>Vor dem Embed</p>"))
        assertTrue(sanitized.contains("<p>Nach dem Embed</p>"))
        assertFalse(sanitized.contains("<object", ignoreCase = true))
        assertFalse(sanitized.contains("<embed", ignoreCase = true))
        assertFalse(sanitized.contains("movie.swf"))
        assertFalse(sanitized.contains("plugin.swf"))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesLegacyFrames() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Vor dem Frame</p>
                <frameset cols="50%,50%"><frame src="https://evil.example/frame.html"></frame></frameset>
                <p>Nach dem Frame</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<p>Vor dem Frame</p>"))
        assertTrue(sanitized.contains("<p>Nach dem Frame</p>"))
        assertFalse(sanitized.contains("<frameset", ignoreCase = true))
        assertFalse(sanitized.contains("<frame", ignoreCase = true))
        assertFalse(sanitized.contains("frame.html"))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesAppletTags() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Vor dem Applet</p>
                <applet code="EvilApplet.class" archive="https://evil.example/archive.jar">Applet-Inhalt</applet>
                <p>Nach dem Applet</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<p>Vor dem Applet</p>"))
        assertTrue(sanitized.contains("<p>Nach dem Applet</p>"))
        assertFalse(sanitized.contains("<applet", ignoreCase = true))
        assertFalse(sanitized.contains("EvilApplet.class"))
        assertFalse(sanitized.contains("archive.jar"))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesLegacyMiscTags() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Vor den Alt-Tags</p>
                <xml src="https://evil.example/legacy.xml"></xml>
                <isindex prompt="Suche">
                <nextid n="z42">
                <scriptlet src="https://evil.example/legacy.sct"></scriptlet>
                <p>Nach den Alt-Tags</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<p>Vor den Alt-Tags</p>"))
        assertTrue(sanitized.contains("<p>Nach den Alt-Tags</p>"))
        assertFalse(sanitized.contains("<xml", ignoreCase = true))
        assertFalse(sanitized.contains("<isindex", ignoreCase = true))
        assertFalse(sanitized.contains("<nextid", ignoreCase = true))
        assertFalse(sanitized.contains("<scriptlet", ignoreCase = true))
        assertFalse(sanitized.contains("legacy.xml"))
        assertFalse(sanitized.contains("legacy.sct"))
    }

    @Test
    fun sanitizeReaderBodyHtmlRemovesLegacyPresentationTagsButKeepsVisibleFallbackText() {
        val sanitized = sanitizeReaderBodyHtmlForTest(
            """
                <p>Vor dem Alt-Layout</p>
                <bgsound src="https://evil.example/alarm.wav">
                <basefont size="7">
                <param name="autoplay" value="true">
                <spacer type="block" width="10" height="10">
                <plaintext>
                <marquee behavior="scroll">Lauftext bleibt lesbar</marquee>
                <blink>Warntext bleibt lesbar</blink>
                <noembed>Fallback ohne Plugin bleibt lesbar</noembed>
                <noframes>Fallback ohne Frames bleibt lesbar</noframes>
                <nobr>Umbruchschutz bleibt lesbar</nobr>
                <multicol cols="2">Mehrspaltiger Alttext bleibt lesbar</multicol>
                <center>Zentrierter Alttext bleibt lesbar</center>
                <font color="red">Alter Schrifttext bleibt lesbar</font>
                <acronym title="Really Simple Syndication">RSS bleibt lesbar</acronym>
                <tt>Schreibmaschinen-Alttext bleibt lesbar</tt>
                <strike>Durchgestrichener Alttext bleibt lesbar</strike>
                <big>Vergrösserter Alttext bleibt lesbar</big>
                <listing>Listenartiger Alttext bleibt lesbar</listing>
                <xmp>Vorformatierter Alttext bleibt lesbar</xmp>
                <layer>Schicht-Alttext bleibt lesbar</layer>
                <ilayer>Inline-Schicht bleibt lesbar</ilayer>
                <nolayer>Ohne Schicht bleibt lesbar</nolayer>
                <shadow>Schatten-Alttext bleibt lesbar</shadow>
                <banner>Banner-Alttext bleibt lesbar</banner>
                <p>Nach dem Alt-Layout</p>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<p>Vor dem Alt-Layout</p>"))
        assertTrue(sanitized.contains("<p>Nach dem Alt-Layout</p>"))
        assertTrue(sanitized.contains("Lauftext bleibt lesbar"))
        assertTrue(sanitized.contains("Warntext bleibt lesbar"))
        assertTrue(sanitized.contains("Fallback ohne Plugin bleibt lesbar"))
        assertTrue(sanitized.contains("Fallback ohne Frames bleibt lesbar"))
        assertTrue(sanitized.contains("Umbruchschutz bleibt lesbar"))
        assertTrue(sanitized.contains("Mehrspaltiger Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Zentrierter Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Alter Schrifttext bleibt lesbar"))
        assertTrue(sanitized.contains("RSS bleibt lesbar"))
        assertTrue(sanitized.contains("Schreibmaschinen-Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Durchgestrichener Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Vergrösserter Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Listenartiger Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Vorformatierter Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Schicht-Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Inline-Schicht bleibt lesbar"))
        assertTrue(sanitized.contains("Ohne Schicht bleibt lesbar"))
        assertTrue(sanitized.contains("Schatten-Alttext bleibt lesbar"))
        assertTrue(sanitized.contains("Banner-Alttext bleibt lesbar"))
        assertFalse(sanitized.contains("<bgsound", ignoreCase = true))
        assertFalse(sanitized.contains("<basefont", ignoreCase = true))
        assertFalse(sanitized.contains("<param", ignoreCase = true))
        assertFalse(sanitized.contains("<spacer", ignoreCase = true))
        assertFalse(sanitized.contains("<plaintext", ignoreCase = true))
        assertFalse(sanitized.contains("<marquee", ignoreCase = true))
        assertFalse(sanitized.contains("<blink", ignoreCase = true))
        assertFalse(sanitized.contains("<noembed", ignoreCase = true))
        assertFalse(sanitized.contains("<noframes", ignoreCase = true))
        assertFalse(sanitized.contains("<nobr", ignoreCase = true))
        assertFalse(sanitized.contains("<multicol", ignoreCase = true))
        assertFalse(sanitized.contains("<center", ignoreCase = true))
        assertFalse(sanitized.contains("<font", ignoreCase = true))
        assertFalse(sanitized.contains("<acronym", ignoreCase = true))
        assertFalse(sanitized.contains("<tt", ignoreCase = true))
        assertFalse(sanitized.contains("<strike", ignoreCase = true))
        assertFalse(sanitized.contains("<big", ignoreCase = true))
        assertFalse(sanitized.contains("<listing", ignoreCase = true))
        assertFalse(sanitized.contains("<xmp", ignoreCase = true))
        assertFalse(sanitized.contains("<layer", ignoreCase = true))
        assertFalse(sanitized.contains("<ilayer", ignoreCase = true))
        assertFalse(sanitized.contains("<nolayer", ignoreCase = true))
        assertFalse(sanitized.contains("<shadow", ignoreCase = true))
        assertFalse(sanitized.contains("<banner", ignoreCase = true))
        assertFalse(sanitized.contains("alarm.wav"))
    }

    private fun article(plainText: String): ArticleEntity {
        return ArticleEntity(
            id = 1,
            feedId = 7,
            uniqueKey = "reader-1",
            title = "Reader Test",
            link = "https://example.com/reader",
            publishedAt = 1_700_000_000_000,
            plainText = plainText,
            contentHtml = "",
            imageUrls = ""
        )
    }

    @Test
    fun resolveArticleLinkAllowsAbsoluteHttpsWithFragment() {
        assertEquals(
            "https://example.com/post#comments",
            resolveArticleLinkForExternalOpen(
                "https://example.com/post#comments",
                "https://example.com/"
            )
        )
    }

    @Test
    fun resolveArticleLinkResolvesFragmentOnlyAgainstArticleUrl() {
        assertEquals(
            "https://example.com/post/#comments",
            resolveArticleLinkForExternalOpen(
                "#comments",
                "https://example.com/post/"
            )
        )
    }

    @Test
    fun resolveArticleLinkResolvesRootRelativeFragment() {
        assertEquals(
            "https://example.com/post/#comments",
            resolveArticleLinkForExternalOpen(
                "/post/#comments",
                "https://example.com/"
            )
        )
    }

    @Test
    fun resolveArticleLinkAllowsMailto() {
        assertEquals(
            "mailto:test@example.com",
            resolveArticleLinkForExternalOpen(
                "mailto:test@example.com",
                "https://example.com/"
            )
        )
    }

    @Test
    fun resolveArticleLinkBlocksJavascript() {
        assertEquals(
            null,
            resolveArticleLinkForExternalOpen(
                "javascript:alert(1)",
                "https://example.com/"
            )
        )
    }

    @Test
    fun resolveArticleLinkBlocksIntent() {
        assertEquals(
            null,
            resolveArticleLinkForExternalOpen(
                "intent://example",
                "https://example.com/"
            )
        )
    }

    @Test
    fun resolveArticleLinkReturnsNullForBlankHref() {
        assertEquals(
            null,
            resolveArticleLinkForExternalOpen("", "https://example.com/")
        )
        assertEquals(
            null,
            resolveArticleLinkForExternalOpen(null, "https://example.com/")
        )
    }
}
