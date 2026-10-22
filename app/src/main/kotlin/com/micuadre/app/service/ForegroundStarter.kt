package com.micuadre.app.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import javax.inject.Inject

/**
 * Seam over `Service.startForeground`, which the system may refuse (for example
 * `ForegroundServiceStartNotAllowedException` on API 31+ when a sticky restart happens while the
 * app is in the background). Implementations let that failure propagate; [ShiftService] handles it.
 */
interface ForegroundStarter {
    fun start(service: Service, notificationId: Int, notification: Notification)
}

/** Real implementation: declares the special-use type on API 34+, where it is required. */
class PlatformForegroundStarter @Inject constructor() : ForegroundStarter {
    override fun start(service: Service, notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            service.startForeground(notificationId, notification)
        }
    }
}
