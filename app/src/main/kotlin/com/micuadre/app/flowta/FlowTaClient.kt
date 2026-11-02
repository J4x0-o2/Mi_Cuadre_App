package com.micuadre.app.flowta

import android.content.Context
import android.net.Uri
import com.micuadre.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Whether FlowTa has a saved mapping for this app. [UNAVAILABLE] covers every failure to ask. */
enum class FlowTaLinkStatus {
    LINKED,
    NOT_LINKED,
    UNAVAILABLE;

    companion object {
        /** Maps the `status` string of a `linkStatus` result; anything unknown is [UNAVAILABLE]. */
        fun fromResult(status: String?): FlowTaLinkStatus = when (status) {
            "LINKED" -> LINKED
            "NOT_LINKED" -> NOT_LINKED
            else -> UNAVAILABLE
        }
    }
}

/** Talks to FlowTa's content provider (contract v1). */
interface FlowTaClient {
    /** Blocking provider call; implementations move it off the main thread. */
    suspend fun linkStatus(): FlowTaLinkStatus
}

class ContentResolverFlowTaClient @Inject constructor(
    @ApplicationContext private val context: Context
) : FlowTaClient {

    override suspend fun linkStatus(): FlowTaLinkStatus = withContext(Dispatchers.IO) {
        try {
            val result = context.contentResolver.call(AUTHORITY_URI, METHOD_LINK_STATUS, null, null)
            FlowTaLinkStatus.fromResult(result?.getString(KEY_STATUS))
        } catch (e: Exception) {
            // Missing provider, key mismatch (SecurityException) or a provider crash: do not alarm the user.
            FlowTaLinkStatus.UNAVAILABLE
        }
    }

    private companion object {
        val AUTHORITY_URI: Uri = Uri.parse("content://${BuildConfig.FLOWTA_AUTHORITY}")
        const val METHOD_LINK_STATUS = "linkStatus"
        const val KEY_STATUS = "status"
    }
}
