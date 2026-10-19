package com.micuadre.app.domain.repository

import com.micuadre.app.domain.model.Platform
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of a platform write. Validation failures are values, not exceptions; storage failures
 * (disk full, locked database) still surface as exceptions.
 */
sealed interface PlatformResult {
    data object Ok : PlatformResult

    /** The name is empty after trimming. */
    data object Blank : PlatformResult

    /** The trimmed name exceeds [com.micuadre.app.domain.model.PlatformName.MAX_LENGTH]. */
    data object TooLong : PlatformResult

    /** Another non-deleted platform already has this name (case-insensitive). */
    data object Duplicate : PlatformResult

    /** The platform does not exist or was already deleted. */
    data object NotFound : PlatformResult
}

/** Management of the user-defined platforms edited from Settings. */
interface PlatformRepository {

    /** Streams the platforms shown in Settings: not deleted (hidden ones included), in creation order. */
    fun observeActive(): Flow<List<Platform>>

    /** Adds a visible platform named [name] (trimmed) at the end of the list. */
    suspend fun create(name: String): PlatformResult

    /** Renames the platform; keeping its own name with a different case is allowed. */
    suspend fun rename(id: Long, name: String): PlatformResult

    /** Shows or hides the platform in the finish-trip sheet. */
    suspend fun setVisible(id: Long, visible: Boolean): PlatformResult

    /** Soft deletes the platform: past trips keep pointing at it, so history is unchanged. */
    suspend fun delete(id: Long): PlatformResult
}
