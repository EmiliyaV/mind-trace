package dev.repetition.scheduling

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class RepetitionStatus {
    PENDING,
    COMPLETED,
    CANCELLED
}

data class Repetition(
    val id: UUID,
    val noteId: UUID,
    val scheduledOn: LocalDate,
    val status: RepetitionStatus,
    val completedAt: Instant?
)

interface RepetitionRepository {
    suspend fun findById(id: UUID): Repetition?
    suspend fun findByNoteId(noteId: UUID): List<Repetition>
    suspend fun findDue(onDate: LocalDate): List<Repetition>
    suspend fun saveInitial(noteId: UUID, repetitions: List<Repetition>)
}
