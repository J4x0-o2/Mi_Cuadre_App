package com.micuadre.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An income waiting to be delivered to FlowTa, one row per finished trip. The row has no foreign
 * key on purpose: it must survive the trip or its session being deleted until it is delivered.
 * [paymentMethod] is the `PaymentMethod` name, stored as text like on the trip.
 */
@Entity(tableName = "pending_flowta_income")
data class PendingFlowTaIncomeEntity(
    @PrimaryKey
    val tripId: Long,
    val amountMinor: Long,
    val paymentMethod: String,
    val endedAt: Long,
    val platformName: String,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long
)
