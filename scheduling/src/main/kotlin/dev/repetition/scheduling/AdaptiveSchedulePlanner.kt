package dev.repetition.scheduling

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ScheduleChange(
    val modified: List<Repetition>,
    val created: List<Repetition>
)

class AdaptiveSchedulePlanner {
    private val restartOffsets = listOf(1L, 3L, 7L, 14L, 30L)

    fun adjust(
        completed: Repetition,
        remaining: List<Repetition>,
        score: Int,
        actualDate: LocalDate
    ): ScheduleChange {
        require(score in 0..100)
        require(completed.status == RepetitionStatus.PENDING)
        require(!actualDate.isBefore(completed.scheduledOn))
        require(remaining.all {
            it.noteId == completed.noteId && it.id != completed.id &&
                it.status == RepetitionStatus.PENDING && !it.scheduledOn.isBefore(completed.scheduledOn)
        })

        val overdueDays = ChronoUnit.DAYS.between(completed.scheduledOn, actualDate)
        val shifted = remaining.map { it.copy(scheduledOn = it.scheduledOn.plusDays(overdueDays)) }
        return when (score) {
            in 0..49 -> ScheduleChange(
                modified = remaining.map { it.copy(status = RepetitionStatus.CANCELLED) },
                created = restartOffsets.map { offset ->
                    Repetition(
                        id = UUID.randomUUID(),
                        noteId = completed.noteId,
                        scheduledOn = actualDate.plusDays(offset),
                        status = RepetitionStatus.PENDING,
                        completedAt = null
                    )
                }
            )
            in 50..79 -> {
                val earlyDate = actualDate.plusDays(2)
                val additional = if (shifted.any { it.scheduledOn == earlyDate }) emptyList() else listOf(
                    Repetition(
                        id = UUID.randomUUID(),
                        noteId = completed.noteId,
                        scheduledOn = earlyDate,
                        status = RepetitionStatus.PENDING,
                        completedAt = null
                    )
                )
                ScheduleChange(shifted, additional)
            }
            else -> ScheduleChange(shifted, emptyList())
        }
    }
}
