package com.micuadre.app.data.repository

import androidx.room.withTransaction
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.data.local.entity.DeductionEntity
import com.micuadre.app.data.local.entity.SessionDeductionEntity
import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.DeductionName
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.DeductionResult
import com.micuadre.app.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeductionRepositoryImpl @Inject constructor(
    private val db: MiCuadreDatabase,
    private val clock: Clock
) : DeductionRepository {

    private val dao = db.deductionDao()
    private val amountDao = db.sessionDeductionDao()

    override fun observeActive(): Flow<List<Deduction>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeVisible(): Flow<List<Deduction>> =
        dao.observeVisible().map { list -> list.map { it.toDomain() } }

    override suspend fun create(name: String, usualAmountMinor: Long?): DeductionResult {
        val normalized = DeductionName.normalize(name)
        validate(normalized, usualAmountMinor)?.let { return it }
        // Check and insert in one transaction so two quick writes cannot both pass the check.
        return db.withTransaction {
            if (isTaken(normalized, exceptId = null)) return@withTransaction DeductionResult.Duplicate
            dao.insert(
                DeductionEntity(
                    name = normalized,
                    visible = true,
                    usualAmountMinor = usualAmountMinor,
                    createdAt = clock.nowMs()
                )
            )
            DeductionResult.Ok
        }
    }

    override suspend fun rename(id: Long, name: String): DeductionResult {
        val normalized = DeductionName.normalize(name)
        validate(normalized, null)?.let { return it }
        return db.withTransaction {
            if (isTaken(normalized, exceptId = id)) return@withTransaction DeductionResult.Duplicate
            if (dao.rename(id, normalized) > 0) DeductionResult.Ok else DeductionResult.NotFound
        }
    }

    override suspend fun setVisible(id: Long, visible: Boolean): DeductionResult =
        if (dao.setVisible(id, visible) > 0) DeductionResult.Ok else DeductionResult.NotFound

    override suspend fun setUsualAmount(id: Long, amountMinor: Long?): DeductionResult {
        if (amountMinor != null && amountMinor < 0) return DeductionResult.InvalidAmount
        return if (dao.setUsualAmount(id, amountMinor) > 0) DeductionResult.Ok else DeductionResult.NotFound
    }

    override suspend fun delete(id: Long): DeductionResult =
        if (dao.softDelete(id, clock.nowMs()) > 0) DeductionResult.Ok else DeductionResult.NotFound

    override suspend fun saveSessionAmounts(sessionId: Long, amounts: Map<Long, Long>): DeductionResult {
        if (amounts.values.any { it < 0 }) return DeductionResult.InvalidAmount
        return db.withTransaction {
            if (amounts.keys.any { dao.byId(it) == null }) return@withTransaction DeductionResult.NotFound
            amountDao.deleteBySession(sessionId)
            val rows = amounts.filterValues { it > 0 }.map { (deductionId, amount) ->
                SessionDeductionEntity(sessionId = sessionId, deductionId = deductionId, amountMinor = amount)
            }
            if (rows.isNotEmpty()) amountDao.upsert(rows)
            DeductionResult.Ok
        }
    }

    override suspend fun sessionAmounts(sessionId: Long): List<SessionDeductionAmount> {
        val names = dao.listAll().associate { it.id to it.name }
        return amountDao.listBySession(sessionId).map { it.toDomain(names) }
    }

    override fun observeAmountsStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionAmount>> =
        combine(amountDao.observeStartedBetween(fromMs, toMs), dao.observeAll()) { rows, all ->
            val names = all.associate { it.id to it.name }
            rows.map { it.toDomain(names) }
        }

    private fun validate(normalized: String, usualAmountMinor: Long?): DeductionResult? = when {
        normalized.isEmpty() -> DeductionResult.Blank
        normalized.length > DeductionName.MAX_LENGTH -> DeductionResult.TooLong
        usualAmountMinor != null && usualAmountMinor < 0 -> DeductionResult.InvalidAmount
        else -> null
    }

    /** Done in Kotlin because the NOCASE collation of SQLite only folds ASCII letters. */
    private suspend fun isTaken(normalized: String, exceptId: Long?): Boolean =
        dao.listAll().any { it.deletedAt == null && it.id != exceptId && DeductionName.sameName(it.name, normalized) }

    private fun DeductionEntity.toDomain() = Deduction(
        id = id,
        name = name,
        visible = visible,
        usualAmountMinor = usualAmountMinor,
        createdAt = createdAt,
        deletedAt = deletedAt
    )

    private fun SessionDeductionEntity.toDomain(names: Map<Long, String>) = SessionDeductionAmount(
        sessionId = sessionId,
        deductionId = deductionId,
        name = names.getValue(deductionId),
        amountMinor = amountMinor
    )
}
