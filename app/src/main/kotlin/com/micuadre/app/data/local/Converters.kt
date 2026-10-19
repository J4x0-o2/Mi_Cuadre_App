package com.micuadre.app.data.local

import androidx.room.TypeConverter
import com.micuadre.app.domain.model.PaymentMethod

/**
 * Stores enums as their `name` string to keep the schema stable across
 * renames and reorderings.
 */
class Converters {

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod? =
        value?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }
}
