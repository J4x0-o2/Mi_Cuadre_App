package com.micuadre.app.data.local

import androidx.room.TypeConverter
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform

/**
 * Stores enums as their `name` string to keep the schema stable across
 * renames and reorderings.
 */
class Converters {

    @TypeConverter
    fun fromPlatform(value: Platform?): String? = value?.name

    @TypeConverter
    fun toPlatform(value: String?): Platform? =
        value?.let { runCatching { Platform.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod? =
        value?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }
}
