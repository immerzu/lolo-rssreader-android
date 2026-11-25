package de.lolo.rssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * RSS-A32: reproduzierbare Messung von Verarbeitungsdauer, Speicherbedarf und
 * Kuerzungsverhalten des Parsers fuer Artikel unterhalb, an und oberhalb der
 * 1.000.000-Zeichen-Grenze sowie fuer einen Feed mit mehreren grossen Artikeln.
 *
 * Die Messung laeuft auf der JVM (Robolectric). Sie ersetzt **keine** Messung auf
 * API 26/36 (dafuer fehlen freigegebene Geraete/Emulatoren), liefert aber reproduzierbare
 * Vergleichswerte und sichert die Stabilitat (keine Exception, korrekte Kuerzung).
 *
 * Ergebnisse werden zusaetzlich nach `build/test-perf/parser-performance.txt` geschrieben.
 */
@RunWith(RobolectricTestRunner::class)
class FeedParserPerformanceTest {

    private val parser = FeedParser()
    private val absoluteLimit = 1_000_000

    private data class Measurement(
        val name: String,
        val inputChars: Int,
        val items: Int,
        val maxContentChars: Int,
        val plainChars: Int,
        val durationMs: Long,
        val heapDeltaKb: Long
    )

    private fun feed(vararg contents: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("<rss version=\"2.0\"><channel>")
        append("<title>Perf</title><link>https://example.com/</link><description>Perf</description>")
        contents.forEachIndexed { index, content ->
            append("<item><title>Artikel $index</title>")
            append("<link>https://example.com/$index</link>")
            append("<guid>$index</guid><description>")
            append(content)
            append("</description></item>")
        }
        append("</channel></rss>")
    }

    private fun measure(name: String, vararg contents: String): Measurement {
        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(120)
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val startedAt = System.nanoTime()
        val parsed = parser.parse(feed(*contents), "https://example.com/feed")
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()

        return Measurement(
            name = name,
            inputChars = contents.sumOf { it.length },
            items = parsed.items.size,
            maxContentChars = parsed.items.maxOfOrNull { it.contentHtml.length } ?: 0,
            plainChars = parsed.items.sumOf { it.plainText.length },
            durationMs = durationMs,
            heapDeltaKb = (heapAfter - heapBefore) / 1024
        )
    }

    @Test
    fun measureBoundariesAndMultiArticleFeed() {
        val results = listOf(
            measure("unter_Grenze_900k", "x".repeat(900_000)),
            measure("an_Grenze_1000k", "y".repeat(1_000_000)),
            measure("ueber_Grenze_1100k", "z".repeat(1_100_000)),
            measure("fuenf_Artikel_je_900k", *Array(5) { "a".repeat(900_000) })
        )

        val reportFile = File("build/test-perf/parser-performance.txt")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("name|inputChars|items|maxContentChars|plainChars|durationMs|heapDeltaKb")
                results.forEach { m ->
                    appendLine(
                        "${m.name}|${m.inputChars}|${m.items}|${m.maxContentChars}|" +
                            "${m.plainChars}|${m.durationMs}|${m.heapDeltaKb}"
                    )
                }
            }
        )
        println("PERF-REPORT:\n" + reportFile.readText())

        results.forEach { m ->
            assertTrue("${m.name}: Artikel muss erhalten bleiben", m.items > 0)
            assertTrue(
                "${m.name}: Inhalt darf die harte Grenze nicht ueberschreiten",
                m.maxContentChars <= absoluteLimit
            )
            assertTrue(
                "${m.name}: Verarbeitung muss stabil und schnell genug sein (war ${m.durationMs} ms)",
                m.durationMs < 20_000
            )
        }

        // Kuerzung greift nur oberhalb der Grenze.
        val exact = results.single { it.name == "an_Grenze_1000k" }
        assertEquals(absoluteLimit, exact.maxContentChars)
        val above = results.single { it.name == "ueber_Grenze_1100k" }
        assertTrue("Oberhalb der Grenze muss gekuerzt werden", above.maxContentChars <= absoluteLimit)
        val multi = results.single { it.name == "fuenf_Artikel_je_900k" }
        assertEquals("Alle fuenf Artikel muessen geparst werden", 5, multi.items)
        assertEquals("Jeder Artikel behaelt seinen Inhalt", 900_000, multi.maxContentChars)
    }
}
