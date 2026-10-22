package dev.repetition.scheduling

import java.time.LocalDate
import java.util.UUID

fun interface SchedulePlanner {
    fun generate(noteId: UUID, creationDate: LocalDate): List<Repetition>
}

class FixedIntervalSchedulePlanner : SchedulePlanner {
    private val offsets = listOf(1L, 3L, 7L, 14L, 30L)

    override fun generate(noteId: UUID, creationDate: LocalDate): List<Repetition> =
        offsets.map { offset ->
            Repetition(
                id = UUID.randomUUID(),
                noteId = noteId,
                scheduledOn = creationDate.plusDays(offset),
                status = RepetitionStatus.PENDING,
                completedAt = null
            )
        }
}
