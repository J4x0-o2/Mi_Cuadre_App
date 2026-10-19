package com.micuadre.app.domain.model

/** Summary of the managed platforms: how many are shown in the finish-trip sheet out of all. */
data class PlatformCounts(val visible: Int, val total: Int) {
    companion object {
        fun of(platforms: List<Platform>): PlatformCounts {
            val active = platforms.filterNot { it.isDeleted }
            return PlatformCounts(visible = active.count { it.visible }, total = active.size)
        }
    }
}
