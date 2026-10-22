package dev.repetition.scheduling

import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdaptiveScheduleBoundaryStage6Test {
    private val noteId = UUID.randomUUID()
    private val planner = AdaptiveSchedulePlanner()
    private val today = LocalDate.of(2026, 12, 31)

    @Test
    fun everyThresholdChoosesRequiredStrategy() {
        val completed = pending(today.minusDays(3))
        val future = listOf(pending(today.plusDays(4)), pending(today.plusDays(9)))
        for (score in listOf(0, 49)) {
            val change = planner.adjust(completed, future, score, today)
            assertEquals(2, change.modified.count { it.status == RepetitionStatus.CANCELLED })
            assertEquals(listOf(1L, 3L, 7L, 14L, 30L).map(today::plusDays),
                change.created.map { it.scheduledOn })
        }
        for (score in listOf(50, 79)) {
            val change = planner.adjust(completed, future, score, today)
            assertEquals(listOf(today.plusDays(2)), change.created.map { it.scheduledOn })
            assertTrue(change.modified.all { it.status == RepetitionStatus.PENDING })
        }
        for (score in listOf(80, 100)) {
            val change = planner.adjust(completed, future, score, today)
            assertTrue(change.created.isEmpty())
            assertEquals(listOf(today.plusDays(7), today.plusDays(12)),
                change.modified.map { it.scheduledOn })
        }
    }

    @Test
    fun mediumScoreDoesNotDuplicateShiftedDateAcrossYearBoundary() {
        val completed = pending(today.minusDays(3))
        val next = pending(today.minusDays(1))
        val result = planner.adjust(completed, listOf(next), 79, today)
        assertTrue(result.created.isEmpty())
        assertEquals(LocalDate.of(2027, 1, 2), result.modified.single().scheduledOn)
    }

    @Test
    fun invalidScoresAndUnexpectedRepetitionsAreRejected() {
        val completed = pending(today)
        assertFailsWith<IllegalArgumentException> {
            planner.adjust(completed, emptyList(), -1, today)
        }
        assertFailsWith<IllegalArgumentException> {
            planner.adjust(completed, emptyList(), 101, today)
        }
        assertFailsWith<IllegalArgumentException> {
            planner.adjust(completed, listOf(pending(today.minusDays(1))), 80, today)
        }
    }

    private fun pending(day: LocalDate) = Repetition(
        UUID.randomUUID(), noteId, day, RepetitionStatus.PENDING, null
    )
}
