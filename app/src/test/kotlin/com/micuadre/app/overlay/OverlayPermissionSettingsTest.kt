package com.micuadre.app.overlay

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class OverlayPermissionSettingsTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Make startActivity throw ActivityNotFoundException for intents nothing resolves.
        shadowOf(app).checkActivities(true)
    }

    private fun resolvable(intent: Intent) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.Target"
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(intent, info)
    }

    @Test
    fun opensTheAppSpecificOverlayPageWhenAvailable() {
        resolvable(OverlayPermissionSettings.candidates(app.packageName).first())

        assertTrue(OverlayPermissionSettings.open(app))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, started.action)
        assertEquals("package:${app.packageName}", started.dataString)
    }

    @Test
    fun fallsBackToTheAppDetailsPageWhenTheOverlayPagesAreMissing() {
        resolvable(OverlayPermissionSettings.candidates(app.packageName).last())

        assertTrue(OverlayPermissionSettings.open(app))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
    }

    @Test
    fun returnsFalseWithoutCrashingWhenNoSettingsPageExists() {
        assertFalse(OverlayPermissionSettings.open(app))
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
