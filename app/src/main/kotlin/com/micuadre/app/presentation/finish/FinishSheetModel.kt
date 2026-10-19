package com.micuadre.app.presentation.finish

import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PlatformSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Everything the finish sheet needs to render; null in HomeUiState while the sheet is closed. */
data class FinishSheetState(
    val tripStartedAtMs: Long,
    val tripEndedAtMs: Long,
    val initialSelection: PlatformSelection,
    /** Visible platforms to choose from, in creation order; empty when the user must enable one. */
    val platforms: List<Platform> = emptyList()
)

/** Builds the "HH:mm – HH:mm · N min" subtitle under the sheet title. */
object FinishSheetSubtitle {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun format(startMs: Long, endMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = timeFormat.format(Instant.ofEpochMilli(startMs).atZone(zone))
        val end = timeFormat.format(Instant.ofEpochMilli(endMs).atZone(zone))
        val minutes = TimeUnit.MILLISECONDS.toMinutes((endMs - startMs).coerceAtLeast(0L)).coerceAtLeast(1L)
        return "$start – $end · $minutes min"
    }
}
