package com.example.rssreader.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.repository.OpmlImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val NO_NETWORK_CONNECTION_MESSAGE = "Keine Netzwerkverbindung"

internal suspend fun importOpmlFromUri(
    context: Context,
    repository: FeedRepository,
    uri: Uri
) = runCatching {
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        repository.importOpml(inputStream)
    } ?: throw RssReaderException.FileReadFailed()
}.getOrElse { throwable ->
    if (throwable is CancellationException) {
        throw throwable
    }
    if (throwable is RssReaderException) {
        throw throwable
    }
    throw RssReaderException.FileReadFailed(throwable)
}

internal suspend fun exportOpmlToUri(
    context: Context,
    repository: FeedRepository,
    uri: Uri
) = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        repository.exportOpml(outputStream)
    } ?: throw RssReaderException.FileWriteFailed()
}.getOrElse { throwable ->
    if (throwable is CancellationException) {
        throw throwable
    }
    if (throwable is RssReaderException) {
        throw throwable
    }
    throw RssReaderException.FileWriteFailed(throwable)
}

internal fun findHostActivity(context: Context): ComponentActivity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is ComponentActivity) {
            return current
        }
        current = current.baseContext
    }
    return current as? ComponentActivity
}

internal fun launchFromUiScope(
    activity: ComponentActivity?,
    fallbackScope: CoroutineScope,
    block: suspend () -> Unit
) {
    val stableScope = activity?.lifecycleScope
    if (stableScope != null) {
        stableScope.launch { block() }
    } else {
        fallbackScope.launch { block() }
    }
}

internal fun isDefinitelyOffline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        ?: return true
    val activeNetwork = connectivityManager.activeNetwork ?: return true
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return true

    return !hasRefreshTransport(capabilities)
}

internal fun hasRefreshTransport(capabilities: NetworkCapabilities?): Boolean {
    if (capabilities == null) {
        return false
    }
    return hasRefreshTransportFlags(
        hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    )
}

internal fun hasRefreshTransportFlags(
    hasWifi: Boolean,
    hasCellular: Boolean,
    hasEthernet: Boolean,
    hasVpn: Boolean = false
): Boolean {
    return hasWifi || hasCellular || hasEthernet || hasVpn
}

@Composable
internal fun ImportProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        text = {
            Text(
                text = "Feeds werden importiert",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        },
        confirmButton = {}
    )
}

@Composable
internal fun ImportResultDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

internal fun formatImportResultDialogMessage(result: OpmlImportResult): String {
    return if (result.failedFeeds == 0) {
        "Import erfolgreich"
    } else {
        "Nicht alle Feeds konnten geladen werden\nFehler: ${result.failedFeeds}"
    }
}
