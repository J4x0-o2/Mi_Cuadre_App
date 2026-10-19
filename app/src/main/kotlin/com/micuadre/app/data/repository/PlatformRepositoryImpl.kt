package com.micuadre.app.data.repository

import androidx.room.withTransaction
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.data.local.entity.PlatformEntity
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PlatformName
import com.micuadre.app.domain.repository.PlatformRepository
import com.micuadre.app.domain.repository.PlatformResult
import com.micuadre.app.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformRepositoryImpl @Inject constructor(
    private val db: MiCuadreDatabase,
    private val clock: Clock
) : PlatformRepository {

    private val dao = db.platformDao()

    override fun observeActive(): Flow<List<Platform>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun create(name: String): PlatformResult {
        val normalized = PlatformName.normalize(name)
        validate(normalized)?.let { return it }
        // Check and insert in one transaction so two quick writes cannot both pass the check.
        return db.withTransaction {
            if (isTaken(normalized, exceptId = null)) return@withTransaction PlatformResult.Duplicate
            dao.insert(PlatformEntity(name = normalized, visible = true, createdAt = clock.nowMs()))
            PlatformResult.Ok
        }
    }

    override suspend fun rename(id: Long, name: String): PlatformResult {
        val normalized = PlatformName.normalize(name)
        validate(normalized)?.let { return it }
        return db.withTransaction {
            if (isTaken(normalized, exceptId = id)) return@withTransaction PlatformResult.Duplicate
            if (dao.rename(id, normalized) > 0) PlatformResult.Ok else PlatformResult.NotFound
        }
    }

    override suspend fun setVisible(id: Long, visible: Boolean): PlatformResult =
        if (dao.setVisible(id, visible) > 0) PlatformResult.Ok else PlatformResult.NotFound

    override suspend fun delete(id: Long): PlatformResult =
        if (dao.softDelete(id, clock.nowMs()) > 0) PlatformResult.Ok else PlatformResult.NotFound

    private fun validate(normalized: String): PlatformResult? = when {
        normalized.isEmpty() -> PlatformResult.Blank
        normalized.length > PlatformName.MAX_LENGTH -> PlatformResult.TooLong
        else -> null
    }

    /** Done in Kotlin because SQLite's NOCASE collation only folds ASCII letters. */
    private suspend fun isTaken(normalized: String, exceptId: Long?): Boolean =
        dao.listActive().any { it.id != exceptId && PlatformName.sameName(it.name, normalized) }

    private fun PlatformEntity.toDomain() =
        Platform(id = id, name = name, visible = visible, createdAt = createdAt, deletedAt = deletedAt)
}
