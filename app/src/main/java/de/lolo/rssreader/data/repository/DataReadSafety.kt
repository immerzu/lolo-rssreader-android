package de.lolo.rssreader.data.repository

import de.lolo.rssreader.debug.DebugLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

private const val READ_SAFETY_TAG = "DataRead"

// Ein voruebergehender Lesefehler (z. B. kurzzeitig gesperrte Datenbank) wird begrenzt
// wiederholt; danach endet der Fluss wie zuvor.
internal const val DATABASE_READ_RETRY_ATTEMPTS = 2
internal const val DATABASE_READ_RETRY_DELAY_MS = 500L

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
 * - erfolgreiche Emissionen melden [onData] (die Meldung kann dann zurueckgenommen werden),
 * - voruebergehende Fehler werden bis zu [retries] Mal mit [retryDelayMs] Wartezeit wiederholt.
 *
 * Nach dem letzten erfolglosen Versuch endet der Fluss bewusst. Die UI behaelt dadurch ihren
 * Ladezustand beziehungsweise den letzten Stand und zeigt die Meldung aus [onFailure] -
 * statt einen irrefuehrenden Leerzustand ("keine Feeds", "keine Treffer") vorzutaeuschen.
 * Ein erneutes Oeffnen des Screens abonniert den Fluss neu und versucht es wieder.
 */
internal fun <T> Flow<T>.guardDatabaseRead(
    source: String,
    onFailure: (DataReadFailure) -> Unit,
    onData: () -> Unit = {},
    retries: Int = DATABASE_READ_RETRY_ATTEMPTS,
    retryDelayMs: Long = DATABASE_READ_RETRY_DELAY_MS
): Flow<T> = this
    .onEach { onData() }
    .retryWhen { cause, attempt ->
        if (cause is CancellationException) {
            throw cause
        }
        DebugLogger.w(
            READ_SAFETY_TAG,
            "Lesefehler ($source, Versuch ${attempt + 1}): ${cause.javaClass.simpleName}",
            cause
        )
        onFailure(DataReadFailure(source = source, cause = cause))
        if (attempt < retries) {
            delay(retryDelayMs)
            true
        } else {
            false
        }
    }
    .catch { throwable ->
        // Der finale Fehler wurde im retryWhen bereits gemeldet; hier endet der Fluss
        // geordnet, statt die Exception an den Collector weiterzureichen.
        if (throwable is CancellationException) {
            throw throwable
        }
    }
