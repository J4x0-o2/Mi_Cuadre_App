package com.micuadre.app.flowta

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.micuadre.app.BuildConfig

/** Opens FlowTa's connect screen; the result is true only when the user saved the mapping. */
class ConnectFlowTaContract : ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(ACTION_CONNECT_SOURCE).setPackage(BuildConfig.FLOWTA_PACKAGE)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK

    companion object {
        const val ACTION_CONNECT_SOURCE = "com.j4x.flowta.action.CONNECT_SOURCE"
    }
}
