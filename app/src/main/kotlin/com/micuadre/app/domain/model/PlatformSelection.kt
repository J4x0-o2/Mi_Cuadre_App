package com.micuadre.app.domain.model

/** A platform choice; [otherLabel] is the free-text name when [platform] is [Platform.OTHER]. */
data class PlatformSelection(val platform: Platform, val otherLabel: String? = null)
