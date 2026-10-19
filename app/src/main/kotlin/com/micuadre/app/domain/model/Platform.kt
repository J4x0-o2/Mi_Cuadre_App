package com.micuadre.app.domain.model

/**
 * A ride platform the driver works with (InDriver, Didi, Beat...), managed from Settings.
 *
 * Deleting is soft ([deletedAt] set): past trips keep pointing at the row so history keeps the
 * name and totals, while the platform disappears from Settings and from the finish-trip sheet.
 * Hidden ([visible] false) platforms stay in Settings but are not offered when finishing a trip.
 */
data class Platform(
    val id: Long,
    val name: String,
    val visible: Boolean = true,
    val createdAt: Long = 0L,
    val deletedAt: Long? = null
) {
    val isDeleted: Boolean get() = deletedAt != null
}
