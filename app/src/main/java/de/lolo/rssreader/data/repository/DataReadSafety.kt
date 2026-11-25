package de.lolo.rssreader.data.repository

import de.lolo.rssreader.debug.DebugLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach

private const val READ_SAFETY_TAG = "DataRead"

/**
 * Kennzeichnet einen fehlgeschlagenen Datenbank-Lesevorgang (Audit RSS-A34).
 * [source] benennt den betroffenen Lesepfad; [cause] bleibt fuer das Log erhalten.
 */
data class DataReadFailure(
    val source: String,
    val cause: Throwable
)

/**
 * Sichert einen Room-Lesefluss ab:
 * - Coroutine-Abbruch wird unveraendert weitergereicht (kein Verschlucken),
 * - jeder andere Lesefehler wird protokolliert und ueber [onFailure] gemeldet,
 * - erfolgreiche Emissionen melden [onData] (die Meldung kann dann zurueckgenommen werden).
 *
 * Nach einem Fehler endet der Fluss bewusst. Die UI behaelt dadurch ihren Ladezustand
 * beziehungsweise den letzten Stand und zeigt die Meldung aus [onFailure] - statt einen
 * irrefuehrenden Leerzustand ("keine Feeds", "keine Treffer") vorzutaeuschen.
 */
internal fun <T> Flow<T>.guardDatabaseRead(
    source: String,
    onFailure: (DataReadFailure) -> Unit,
    onData: () -> Unit = {}
): Flow<T> = this
    .onEach { onData() }
    .catch { throwable ->
        if (throwable is CancellationException) {
            throw throwable
        }
        DebugLogger.w(
            READ_SAFETY_TAG,
            "Lesefehler ($source): ${throwable.javaClass.simpleName}",
            throwable
        )
        onFailure(DataReadFailure(source = source, cause = throwable))
    }
