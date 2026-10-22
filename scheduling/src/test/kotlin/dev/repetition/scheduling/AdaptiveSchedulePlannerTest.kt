package dev.repetition.scheduling

import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveSchedulePlannerTest {
    private val noteId = UUID.randomUUID()
    private val today = LocalDate.of(2026, 10, 22)
    private val completed = repetition(today.minusDays(3))
    private val future = listOf(repetition(today.minusDays(1)), repetition(today.plusDays(4)))
    private val planner = AdaptiveSchedulePlanner()

    @Test
    fun highScorePreservesRemainingDatesAfterOverdueShift() {
        val result = planner.adjust(completed, future, 92, today)
        assertEquals(listOf(today.plusDays(2), today.plusDays(7)), result.modified.map { it.scheduledOn })
        assertTrue(result.created.isEmpty())
    }

    @Test
    fun mediumScoreAddsEarlyReviewWithoutDuplicates() {
        val result = planner.adjust(completed, future, 65, today)
        assertEquals(2, result.modified.size)
        assertTrue(result.created.isEmpty())
        assertEquals(today.plusDays(2), result.modified.first().scheduledOn)
    }

    @Test
    fun mediumScoreCreatesEarlyReviewIfNoMatch() {
        val result = planner.adjust(completed, listOf(future.last()), 50, today)
        assertEquals(listOf(today.plusDays(2)), result.created.map { it.scheduledOn })
    }

    @Test
    fun weakScoreCancelsFutureAndRestartsFromActualDay() {
        val result = planner.adjust(completed, future, 49, today)
        assertTrue(result.modified.all { it.status == RepetitionStatus.CANCELLED })
        assertEquals(listOf(1L, 3L, 7L, 14L, 30L).map(today::plusDays),
            result.created.map { it.scheduledOn })
    }

    @Test
    fun boundaryOfAllThreeScoreCategories() {
        assertEquals(5, planner.adjust(completed, future, 0, today).created.size)
        assertEquals(5, planner.adjust(completed, future, 49, today).created.size)
        assertEquals(0, planner.adjust(completed, future, 79, today).created.size)
        assertEquals(0, planner.adjust(completed, future, 80, today).created.size)
        assertEquals(0, planner.adjust(completed, future, 100, today).created.size)
    }

    @Test
    fun onTimeCompletionDoesNotShiftRemainingDates() {
        val due = repetition(today)
        val after = repetition(today.plusDays(2))
        val result = planner.adjust(due, listOf(after), 100, today)
        assertEquals(after.scheduledOn, result.modified.single().scheduledOn)
    }

    private fun repetition(date: LocalDate) = Repetition(
        UUID.randomUUID(), noteId, date, RepetitionStatus.PENDING, null
    )
}
