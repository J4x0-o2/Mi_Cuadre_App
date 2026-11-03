package com.micuadre.app.flowta

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageManagerFlowTaProviderVerifierTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val verifier = PackageManagerFlowTaProviderVerifier(context)

    private fun install(packageName: String, signature: String, authority: String? = null) {
        val info = PackageInfo().apply {
            this.packageName = packageName
            signatures = arrayOf(Signature(signature.toByteArray().joinToString("") { "%02x".format(it) }))
            if (authority != null) {
                providers = arrayOf(
                    ProviderInfo().apply {
                        this.authority = authority
                        this.packageName = packageName
                        name = "FlowTaProvider"
                        applicationInfo = android.content.pm.ApplicationInfo().apply { this.packageName = packageName }
                    }
                )
            }
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    @Test
    fun `nothing installed is not verified`() {
        assertFalse(verifier.isVerified())
    }

    @Test
    fun `the right package with the same signing key is verified`() {
        install(context.packageName, "key-a")
        install(BuildConfig.FLOWTA_PACKAGE, "key-a", BuildConfig.FLOWTA_AUTHORITY)

        assertTrue(verifier.isVerified())
    }

    @Test
    fun `a different signing key is not verified`() {
        install(context.packageName, "key-a")
        install(BuildConfig.FLOWTA_PACKAGE, "key-b", BuildConfig.FLOWTA_AUTHORITY)

        assertFalse(verifier.isVerified())
    }

    @Test
    fun `an authority owned by another package is not verified`() {
        install(context.packageName, "key-a")
        install(BuildConfig.FLOWTA_PACKAGE, "key-a")
        install("com.evil.app", "key-a", BuildConfig.FLOWTA_AUTHORITY)

        assertFalse(verifier.isVerified())
    }
}
