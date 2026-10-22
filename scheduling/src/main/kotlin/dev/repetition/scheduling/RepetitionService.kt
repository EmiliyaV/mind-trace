package dev.repetition.scheduling

import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class DueRepetitions(
    val asOfDate: LocalDate,
    val dueToday: List<Repetition>,
    val overdue: List<Repetition>
)

class RepetitionService(
    private val repository: RepetitionRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend fun findSchedule(noteId: UUID): List<Repetition> = repository.findByNoteId(noteId)

    suspend fun findDue(): DueRepetitions {
        val date = LocalDate.now(clock)
        val due = repository.findDue(date)
        return DueRepetitions(
            asOfDate = date,
            dueToday = due.filter { it.status == RepetitionStatus.PENDING && it.scheduledOn == date },
            overdue = due.filter { it.status == RepetitionStatus.PENDING && it.scheduledOn.isBefore(date) }
        )
    }
}
