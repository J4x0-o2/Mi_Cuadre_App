package com.micuadre.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a user-defined deduction (fuel, app fee, food...). Rows are never hard deleted once
 * shifts reference them: deleting sets [deletedAt] (soft delete) so history keeps the name.
 * [usualAmountMinor] is an optional prefill for the end-of-shift sheet, in minor units.
 */
@Entity(tableName = "deductions")
data class DeductionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val visible: Boolean,
    val usualAmountMinor: Long? = null,
    val createdAt: Long,
    val deletedAt: Long? = null
)
