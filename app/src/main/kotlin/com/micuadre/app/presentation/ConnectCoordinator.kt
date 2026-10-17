package com.micuadre.app.presentation

/** What the UI knows about the POST_NOTIFICATIONS permission when the user taps Connect. */
enum class NotificationPermission { NotRequired, Granted, NeedsRequest }

/**
 * Decides when the shift service may start relative to the notification
 * permission prompt. Pure and JVM-testable: the actual start is injected.
 *
 * Policy: the service starts only after the permission result is known.
 * A denial never blocks the shift (a foreground service still runs, only its
 * notification may be hidden), but the user is told about it once.
 */
class ConnectCoordinator(private val startShift: () -> Unit) {

    /** Follow-up the UI must perform. */
    enum class Effect { None, RequestPermission, ShowDeniedHint }

    private var awaitingPermission = false

    fun onConnectTapped(permission: NotificationPermission): Effect {
        if (awaitingPermission) return Effect.None
        return when (permission) {
            NotificationPermission.NeedsRequest -> {
                awaitingPermission = true
                Effect.RequestPermission
            }
            NotificationPermission.NotRequired,
            NotificationPermission.Granted -> {
                startShift()
                Effect.None
            }
        }
    }

    fun onPermissionResult(granted: Boolean): Effect {
        if (!awaitingPermission) return Effect.None
        awaitingPermission = false
        startShift()
        return if (granted) Effect.None else Effect.ShowDeniedHint
    }
}
