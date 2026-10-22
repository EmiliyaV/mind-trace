package dev.repetition.scheduling

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RepetitionServiceTest {
    @Test
    fun onlyPendingRepetitionsAreConsideredDue() = runBlocking {
        val noteId = UUID.randomUUID()
        val rows = listOf(
            repetition(noteId, "2026-10-19"),
            repetition(noteId, "2026-10-22"),
            repetition(noteId, "2026-10-25"),
            repetition(noteId, "2026-10-21", RepetitionStatus.COMPLETED, Instant.parse("2026-10-21T12:00:00Z")),
            repetition(noteId, "2026-10-20", RepetitionStatus.CANCELLED)
        )
        val service = RepetitionService(
            InMemoryRepetitionRepository(rows),
            Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC)
        )
        val result = service.findDue()
        assertEquals("2026-10-22", result.asOfDate.toString())
        assertEquals(listOf("2026-10-22"), result.dueToday.map { it.scheduledOn.toString() })
        assertEquals(listOf("2026-10-19"), result.overdue.map { it.scheduledOn.toString() })
    }

    private fun repetition(
        noteId: UUID,
        date: String,
        status: RepetitionStatus = RepetitionStatus.PENDING,
        completedAt: Instant? = null
    ): Repetition = Repetition(UUID.randomUUID(), noteId, LocalDate.parse(date), status, completedAt)
}

private class InMemoryRepetitionRepository(private val rows: List<Repetition>) : RepetitionRepository {
    override suspend fun findById(id: UUID) = rows.find { it.id == id }
    override suspend fun findByNoteId(noteId: UUID) = rows.filter { it.noteId == noteId }
    override suspend fun findDue(onDate: LocalDate) = rows.filter {
        it.status == RepetitionStatus.PENDING && !it.scheduledOn.isAfter(onDate)
    }
    override suspend fun saveInitial(noteId: UUID, repetitions: List<Repetition>) = Unit
}
