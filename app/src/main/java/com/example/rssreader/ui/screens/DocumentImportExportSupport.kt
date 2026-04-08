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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rssreader.R
import androidx.lifecycle.lifecycleScope
import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.repository.OpmlImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun noNetworkConnectionMessage(context: Context): String {
    return context.getString(R.string.shared_no_network_connection)
}

internal fun wifiOnlyRefreshMessage(context: Context): String {
    return context.getString(R.string.shared_wifi_only_refresh)
}

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

internal fun shouldBlockRefreshForWifiOnlySetting(
    refreshOnlyOnWifi: Boolean,
    hasWifiConnection: Boolean
): Boolean {
    return refreshOnlyOnWifi && !hasWifiConnection
}

internal fun shouldBlockRefreshForWifiRequirements(
    globalRefreshOnlyOnWifi: Boolean,
    feedWifiOnly: Boolean,
    hasWifiConnection: Boolean
): Boolean {
    return shouldBlockRefreshForWifiOnlySetting(
        refreshOnlyOnWifi = globalRefreshOnlyOnWifi || feedWifiOnly,
        hasWifiConnection = hasWifiConnection
    )
}

internal fun isRefreshBlockedForWifiOnlySetting(
    context: Context,
    refreshOnlyOnWifi: Boolean
): Boolean {
    return shouldBlockRefreshForWifiOnlySetting(
        refreshOnlyOnWifi = refreshOnlyOnWifi,
        hasWifiConnection = hasWifiRefreshConnection(context)
    )
}

internal fun isRefreshBlockedForWifiRequirements(
    context: Context,
    globalRefreshOnlyOnWifi: Boolean,
    feedWifiOnly: Boolean
): Boolean {
    return shouldBlockRefreshForWifiRequirements(
        globalRefreshOnlyOnWifi = globalRefreshOnlyOnWifi,
        feedWifiOnly = feedWifiOnly,
        hasWifiConnection = hasWifiRefreshConnection(context)
    )
}

internal fun hasWifiRefreshConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        ?: return false
    val activeNetwork = connectivityManager.activeNetwork
        ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

    return hasWifiRefreshTransport(
        capabilities = capabilities,
        isActiveNetworkMetered = connectivityManager.isActiveNetworkMetered
    )
}

internal fun hasWifiRefreshTransport(
    capabilities: NetworkCapabilities?,
    isActiveNetworkMetered: Boolean
): Boolean {
    if (capabilities == null) {
        return false
    }
    return hasWifiRefreshTransportFlags(
        hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        isActiveNetworkMetered = isActiveNetworkMetered
    )
}

internal fun hasWifiRefreshTransportFlags(
    hasWifi: Boolean,
    hasCellular: Boolean,
    hasEthernet: Boolean,
    hasVpn: Boolean,
    isActiveNetworkMetered: Boolean
): Boolean {
    return hasWifi || (hasVpn && !isActiveNetworkMetered && !hasCellular && !hasEthernet)
}

@Composable
internal fun ImportProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        text = {
            Text(
                text = stringResource(R.string.shared_import_in_progress),
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
                Text(stringResource(R.string.common_ok))
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

internal fun formatImportResultDialogMessage(result: OpmlImportResult, context: Context): String {
    return if (result.failedFeeds == 0) {
        context.getString(R.string.shared_import_result_success)
    } else {
        context.getString(R.string.shared_import_result_partial, result.failedFeeds)
    }
}
