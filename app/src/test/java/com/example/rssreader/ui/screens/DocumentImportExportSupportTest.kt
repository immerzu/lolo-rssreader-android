package com.example.rssreader.ui.screens

import android.content.ContextWrapper
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.example.rssreader.data.repository.OpmlImportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DocumentImportExportSupportTest {

    @Test
    fun findHostActivityReturnsDirectActivityInstance() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        assertSame(activity, findHostActivity(activity))
    }

    @Test
    fun findHostActivityUnwrapsNestedContextWrappers() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val wrappedContext = object : ContextWrapper(ContextWrapper(activity)) {}

        assertSame(activity, findHostActivity(wrappedContext))
    }

    @Test
    fun findHostActivityReturnsNullWhenNoActivityIsPresent() {
        val applicationContext = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertNull(findHostActivity(ContextWrapper(applicationContext)))
    }

    @Test
    fun findHostActivityReturnsNullForNestedWrappersWithoutActivity() {
        val applicationContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wrappedContext = object : ContextWrapper(ContextWrapper(applicationContext)) {}

        assertNull(findHostActivity(wrappedContext))
    }

    @Test
    fun launchFromUiScopeUsesActivityLifecycleScopeWhenAvailable() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val cancelledFallbackScope = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.Unconfined)
        var executed = false

        launchFromUiScope(activity, cancelledFallbackScope) {
            executed = true
        }

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(executed)
    }

    @Test
    fun launchFromUiScopeFallsBackWhenNoActivityIsAvailable() {
        val fallbackScope = CoroutineScope(Dispatchers.Unconfined)
        var executed = false

        launchFromUiScope(activity = null, fallbackScope = fallbackScope) {
            executed = true
        }

        assertTrue(executed)
    }

    @Test
    fun launchFromUiScopeDoesNotExecuteCancelledFallbackScopeWhenNoActivityIsAvailable() {
        val cancelledFallbackScope = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.Unconfined)
        var executed = false

        launchFromUiScope(activity = null, fallbackScope = cancelledFallbackScope) {
            executed = true
        }

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(!executed)
    }

    @Test
    fun formatImportResultDialogMessageUsesSuccessMessage() {
        assertEquals(
            "Import erfolgreich",
            formatImportResultDialogMessage(
                OpmlImportResult(
                    importedFeeds = 5,
                    skippedFeeds = 0,
                    failedFeeds = 0
                )
            )
        )
    }

    @Test
    fun formatImportResultDialogMessageUsesPartialFailureMessageWithCount() {
        assertEquals(
            "Nicht alle Feeds konnten geladen werden\nFehler: 2",
            formatImportResultDialogMessage(
                OpmlImportResult(
                    importedFeeds = 3,
                    skippedFeeds = 1,
                    failedFeeds = 2
                )
            )
        )
    }

    @Test
    fun hasRefreshTransportFlagsAcceptsWifiCellularAndEthernet() {
        assertTrue(hasRefreshTransportFlags(hasWifi = true, hasCellular = false, hasEthernet = false))
        assertTrue(hasRefreshTransportFlags(hasWifi = false, hasCellular = true, hasEthernet = false))
        assertTrue(hasRefreshTransportFlags(hasWifi = false, hasCellular = false, hasEthernet = true))
    }

    @Test
    fun hasRefreshTransportFlagsAcceptsVpnTransport() {
        assertTrue(
            hasRefreshTransportFlags(
                hasWifi = false,
                hasCellular = false,
                hasEthernet = false,
                hasVpn = true
            )
        )
    }

    @Test
    fun hasRefreshTransportFlagsRejectsMissingTransport() {
        assertTrue(
            !hasRefreshTransportFlags(
                hasWifi = false,
                hasCellular = false,
                hasEthernet = false
            )
        )
    }

    @Test
    fun shouldBlockRefreshForWifiOnlySettingWhenMeteredNetworkIsUsed() {
        assertTrue(
            shouldBlockRefreshForWifiOnlySetting(
                refreshOnlyOnWifi = true,
                hasWifiConnection = false
            )
        )
    }

    @Test
    fun shouldNotBlockRefreshForWifiOnlySettingWhenUnmeteredNetworkIsUsed() {
        assertTrue(
            !shouldBlockRefreshForWifiOnlySetting(
                refreshOnlyOnWifi = true,
                hasWifiConnection = true
            )
        )
    }

    @Test
    fun shouldNotBlockRefreshWhenWifiOnlySettingIsDisabled() {
        assertTrue(
            !shouldBlockRefreshForWifiOnlySetting(
                refreshOnlyOnWifi = false,
                hasWifiConnection = false
            )
        )
    }

    @Test
    fun shouldBlockRefreshWhenFeedWifiOnlyIsEnabledWithoutWifi() {
        assertTrue(
            shouldBlockRefreshForWifiRequirements(
                globalRefreshOnlyOnWifi = false,
                feedWifiOnly = true,
                hasWifiConnection = false
            )
        )
    }

    @Test
    fun shouldBlockRefreshWhenGlobalWifiOnlyIsEnabledWithoutWifi() {
        assertTrue(
            shouldBlockRefreshForWifiRequirements(
                globalRefreshOnlyOnWifi = true,
                feedWifiOnly = false,
                hasWifiConnection = false
            )
        )
    }

    @Test
    fun shouldAllowRefreshWhenWifiRequirementsAreSatisfied() {
        assertTrue(
            !shouldBlockRefreshForWifiRequirements(
                globalRefreshOnlyOnWifi = false,
                feedWifiOnly = true,
                hasWifiConnection = true
            )
        )
    }

    @Test
    fun hasWifiRefreshTransportRejectsEthernetWithoutWifi() {
        assertTrue(
            !hasWifiRefreshTransportFlags(
                hasWifi = false,
                hasCellular = false,
                hasEthernet = true,
                hasVpn = false,
                isActiveNetworkMetered = false
            )
        )
    }

    @Test
    fun hasWifiRefreshTransportAcceptsWifi() {
        assertTrue(
            hasWifiRefreshTransportFlags(
                hasWifi = true,
                hasCellular = false,
                hasEthernet = false,
                hasVpn = false,
                isActiveNetworkMetered = false
            )
        )
    }

    @Test
    fun hasWifiRefreshTransportAcceptsVpnOnUnmeteredConnectionWithoutCellularOrEthernet() {
        assertTrue(
            hasWifiRefreshTransportFlags(
                hasWifi = false,
                hasCellular = false,
                hasEthernet = false,
                hasVpn = true,
                isActiveNetworkMetered = false
            )
        )
    }
}

