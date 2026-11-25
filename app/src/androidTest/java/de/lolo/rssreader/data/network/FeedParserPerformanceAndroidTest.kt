package de.lolo.rssreader.data.network

import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * RSS-A32 / RSS-AUDIT-007: Leistungsmessung auf einem echten Android-Emulator.
 *
 * Fuer jeden Testfall werden nach einer Aufwaermphase fuenf Durchlaeufe gemessen
 * (Dauer, Prozess-PSS, Ergebnis/Kuerzung). Die Ergebnisse werden als Datei unter
 * `getExternalFilesDir()` abgelegt und per `adb pull` ausgelesen.
 *
 * Die Messung ist bewusst nicht Teil der normalen UI-Test-Suite; sie wird gezielt per
 * `am instrument -e class ...` auf den AVDs `rss_api26` und `rss_api36` gestartet.
 */
@RunWith(AndroidJUnit4::class)
class FeedParserPerformanceAndroidTest {

    private val parser = FeedParser()
    private val absoluteLimit = 1_000_000
    private val runs = 5
    private val tag = "ParserPerf"

    private data class Row(
        val label: String,
        val inputChars: Long,
        val items: Int,
        val maxContentChars: Int,
        val medianMs: Long,
        val maxMs: Long,
        val maxPssKb: Long,
        val maxPssDeltaKb: Long
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

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    @Test
    fun measureBoundariesOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Aufwaermphase (nicht gewertet).
        parser.parse(feed("w".repeat(200_000)), "https://example.com/warmup")

        val cases = listOf(
            "900k" to arrayOf("x".repeat(900_000)),
            "1.000k" to arrayOf("y".repeat(1_000_000)),
            "1.100k" to arrayOf("z".repeat(1_100_000)),
            "5x900k" to Array(5) { "a".repeat(900_000) }
        )

        val rows = mutableListOf<Row>()
        cases.forEach { (label, contents) ->
            val durations = mutableListOf<Long>()
            var maxPss = 0L
            var maxPssDelta = 0L
            var items = 0
            var maxContent = 0

            repeat(runs) {
                val pssBefore = Debug.getPss()
                val startedAt = System.nanoTime()
                val parsed = parser.parse(feed(*contents), "https://example.com/feed")
                val durationMs = (System.nanoTime() - startedAt) / 1_000_000
                val pssAfter = Debug.getPss()

                durations += durationMs
                maxPss = maxOf(maxPss, pssAfter)
                maxPssDelta = maxOf(maxPssDelta, pssAfter - pssBefore)
                items = parsed.items.size
                maxContent = parsed.items.maxOfOrNull { it.contentHtml.length } ?: 0

                assertTrue("$label: Artikel muss erhalten bleiben", items > 0)
                assertTrue(
                    "$label: Inhalt darf 1.000.000 Zeichen nicht ueberschreiten (war $maxContent)",
                    maxContent <= absoluteLimit
                )
                assertTrue("$label: Verarbeitung zu langsam (${durationMs} ms)", durationMs < 20_000)
            }

            rows += Row(
                label = label,
                inputChars = contents.fold(0L) { sum, content -> sum + content.length },
                items = items,
                maxContentChars = maxContent,
                medianMs = median(durations),
                maxMs = durations.maxOrNull() ?: 0L,
                maxPssKb = maxPss,
                maxPssDeltaKb = maxPssDelta
            )
        }

        // Kuerzungsverhalten: an der Grenze unveraendert, oberhalb gekuerzt.
        assertEquals(1_000_000, rows.single { it.label == "1.000k" }.maxContentChars)
        assertEquals(1_000_000, rows.single { it.label == "1.100k" }.maxContentChars)
        assertEquals(5, rows.single { it.label == "5x900k" }.items)

        val api = Build.VERSION.SDK_INT
        val report = buildString {
            appendLine("api|label|inputChars|items|maxContentChars|medianMs|maxMs|maxPssKb|maxPssDeltaKb")
            rows.forEach { r ->
                appendLine(
                    "$api|${r.label}|${r.inputChars}|${r.items}|${r.maxContentChars}|" +
                        "${r.medianMs}|${r.maxMs}|${r.maxPssKb}|${r.maxPssDeltaKb}"
                )
            }
        }
        rows.forEach { Log.i(tag, "api=$api ${it.label} median=${it.medianMs}ms max=${it.maxMs}ms pssMax=${it.maxPssKb}kB pssDelta=${it.maxPssDeltaKb}kB content=${it.maxContentChars}") }

        val reportFile = File(context.getExternalFilesDir(null), "parser-perf-api$api.txt")
        reportFile.writeText(report)
        Log.i(tag, "report=${reportFile.absolutePath}")
        println("PERF-REPORT-API$api\n$report")
    }
}
