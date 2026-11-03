package com.micuadre.app.flowta

import android.content.Context
import android.content.pm.PackageManager
import com.micuadre.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the FlowTa app is installed on this device. */
interface FlowTaAvailability {
    fun isInstalled(): Boolean
}

class PackageManagerFlowTaAvailability @Inject constructor(
    @ApplicationContext private val context: Context
) : FlowTaAvailability {
    override fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(BuildConfig.FLOWTA_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
