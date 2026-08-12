package de.lolo.rssreader.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Zentrale WLAN-Erkennung fuer das Wifi-Only-Gating.
 *
 * Ersetzt die frueheren Duplikate in BackgroundRefreshWorker, RssReaderApp und
 * DocumentImportExportSupport. Liefert true, wenn das aktive Netzwerk WLAN ist
 * oder ein ungemeteretes VPN ohne gleichzeitigen Mobilfunk-/Ethernet-Transport
 * besteht.
 *
 * @param requireInternetCapability wenn true, wird zusaetzlich geprueft, dass
 *   das aktive Netzwerk NET_CAPABILITY_INTERNET meldet. Dies entspricht dem
 *   urspruenglichen Verhalten ausschliesslich des BackgroundRefreshWorker;
 *   die UI-Aufrufer uebergeben hier false.
 */
internal fun hasWifiConnection(
    context: Context,
    requireInternetCapability: Boolean
): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    if (requireInternetCapability && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return false
    }
    return hasWifiTransport(
        hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        isActiveNetworkMetered = connectivityManager.isActiveNetworkMetered
    )
}

/**
 * Reine Transport-Formel der WLAN-Erkennung, getrennt von der Context-Abfrage
 * fuer Testbarkeit.
 */
internal fun hasWifiTransport(
    hasWifi: Boolean,
    hasCellular: Boolean,
    hasEthernet: Boolean,
    hasVpn: Boolean,
    isActiveNetworkMetered: Boolean
): Boolean {
    return hasWifi || (hasVpn && !isActiveNetworkMetered && !hasCellular && !hasEthernet)
}
