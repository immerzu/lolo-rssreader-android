package com.example.rssreader.ui.screens

import android.content.ContextWrapper
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
}
