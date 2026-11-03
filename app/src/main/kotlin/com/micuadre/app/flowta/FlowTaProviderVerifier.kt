package com.micuadre.app.flowta

import android.content.Context
import android.content.pm.PackageManager
import com.micuadre.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Confirms the provider behind FlowTa's authority is the real FlowTa before any income is sent to it. */
fun interface FlowTaProviderVerifier {
    /** True only when the authority belongs to the FlowTa package and that package shares our signing key. */
    fun isVerified(): Boolean
}

class PackageManagerFlowTaProviderVerifier @Inject constructor(
    @ApplicationContext private val context: Context
) : FlowTaProviderVerifier {

    @Suppress("DEPRECATION")
    override fun isVerified(): Boolean {
        val pm = context.packageManager
        val provider = pm.resolveContentProvider(BuildConfig.FLOWTA_AUTHORITY, 0) ?: return false
        if (provider.packageName != BuildConfig.FLOWTA_PACKAGE) return false
        return pm.checkSignatures(context.packageName, BuildConfig.FLOWTA_PACKAGE) == PackageManager.SIGNATURE_MATCH
    }
}
