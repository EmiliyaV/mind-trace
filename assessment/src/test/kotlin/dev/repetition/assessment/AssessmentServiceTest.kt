package dev.repetition.assessment

import dev.repetition.notes.Note
import dev.repetition.notes.NoteAnalysis
import dev.repetition.notes.NoteRepository
import dev.repetition.notes.NoteStatus
import dev.repetition.notes.VerificationAspect
import dev.repetition.notes.VerificationContext
import dev.repetition.scheduling.Repetition
import dev.repetition.scheduling.RepetitionRepository
import dev.repetition.scheduling.RepetitionStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssessmentServiceTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val noteId = UUID.randomUUID()
    private val repetitionId = UUID.randomUUID()
    private val repetition = Repetition(repetitionId, noteId, LocalDate.of(2026, 10, 21), RepetitionStatus.PENDING, null)
    private val note = Note(noteId, "Transactions", "A detailed note", NoteStatus.READY, now)
    private val context = VerificationContext(noteId, listOf(VerificationAspect("Explain ACID")), now)

    @Test
    fun evaluationIsSavedOnlyAfterSuccess() = runBlocking {
        val store = MemoryCompletionStore()
        val service = service(store) { _, _ -> Evaluation(75, "Missing isolation") }
        val result = service.submit(repetitionId, "Atomicity and durability")
        assertEquals(75, result.assessment.evaluation.score)
        assertEquals(1, store.completions)
        assertTrue(!result.replayed)
    }

    @Test
    fun completedRepetitionReturnsSameEvaluationWithoutNewLlmCall() = runBlocking {
        val store = MemoryCompletionStore()
        var calls = 0
        val service = service(store) { _, _ -> calls++; Evaluation(90, "Good coverage") }
        val first = service.submit(repetitionId, "A good explanation")
        val second = service.submit(repetitionId, "A different answer")
        assertEquals(first.assessment.id, second.assessment.id)
        assertTrue(second.replayed)
        assertEquals(1, calls)
        assertEquals(1, store.completions)
    }

    @Test
    fun evaluatorFailureDoesNotCompleteRepetition() = runBlocking {
        val store = MemoryCompletionStore()
        val service = service(store) { _, _ -> throw AssessmentExternalException("Service unavailable") }
        assertFailsWith<AssessmentExternalException> {
            runBlocking { service.submit(repetitionId, "A valid answer") }
        }
        assertEquals(0, store.completions)
    }

    private fun service(
        store: MemoryCompletionStore,
        evaluator: AnswerEvaluator
    ) = AssessmentService(
        NoteStore(note, context),
        RepetitionStore(repetition),
        store,
        evaluator,
        Clock.fixed(now, ZoneOffset.UTC)
    )
}

private class MemoryCompletionStore : AssessmentCompletionStore {
    private var item: Assessment? = null
    var completions: Int = 0
    override suspend fun findByRepetitionId(repetitionId: UUID) =
        item?.takeIf { it.repetitionId == repetitionId }
    override suspend fun completeAtomically(assessment: Assessment): Assessment {
        completions++
        item = assessment
        return assessment
    }
}

private class NoteStore(
    private val note: Note,
    private val context: VerificationContext
) : NoteRepository {
    override suspend fun saveAnalyzed(
        note: Note,
        context: VerificationContext?,
        repetitions: List<Repetition>
    ) = Unit
    override suspend fun findById(id: UUID) = note.takeIf { it.id == id }
    override suspend fun findAll() = listOf(note)
    override suspend fun findContextByNoteId(noteId: UUID) = context.takeIf { it.noteId == noteId }
}

private class RepetitionStore(private val repetition: Repetition) : RepetitionRepository {
    override suspend fun findById(id: UUID) = repetition.takeIf { it.id == id }
    override suspend fun findByNoteId(noteId: UUID) = listOf(repetition).filter { it.noteId == noteId }
    override suspend fun findDue(onDate: LocalDate) = listOf(repetition).filter { it.scheduledOn <= onDate }
    override suspend fun saveInitial(noteId: UUID, repetitions: List<Repetition>) = Unit
}
