package dev.repetition.scheduling

import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixedIntervalSchedulePlannerTest {
    @Test
    fun generatesDatesRelativeToCreationDay() {
        val noteId = UUID.randomUUID()
        val generated = FixedIntervalSchedulePlanner().generate(noteId, LocalDate.of(2026, 10, 22))
        assertEquals(
            listOf("2026-10-23", "2026-10-25", "2026-10-29", "2026-11-05", "2026-11-21"),
            generated.map { it.scheduledOn.toString() }
        )
        assertEquals(5, generated.map { it.id }.distinct().size)
        assertTrue(generated.all {
            it.noteId == noteId && it.status == RepetitionStatus.PENDING && it.completedAt == null
        })
    }

    @Test
    fun handlesMonthAndYearBoundary() {
        val dates = FixedIntervalSchedulePlanner()
            .generate(UUID.randomUUID(), LocalDate.of(2026, 12, 30))
            .map { it.scheduledOn }
        assertEquals(LocalDate.of(2027, 1, 2), dates[1])
        assertEquals(LocalDate.of(2027, 1, 29), dates.last())
    }
}
