package dev.repetition.notes

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import dev.repetition.scheduling.Repetition
import dev.repetition.scheduling.FixedIntervalSchedulePlanner
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NoteServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun sufficientNotesAreSavedWithContext() = runBlocking {
        val store = FakeStore()
        val service = NoteService(store, NoteAnalyzer {
            NoteAnalysis.Sufficient(listOf(VerificationAspect("Main idea")))
        }, clock, FixedIntervalSchedulePlanner())
        val result = service.create("Title", "A detailed note")
        assertEquals(NoteStatus.READY, result.note.status)
        assertNotNull(result.context)
        assertEquals(1, store.notes.size)
        assertEquals(1, store.contexts.size)
        assertEquals(5, store.repetitions.size)
        assertEquals(
            listOf("2026-10-23", "2026-10-25", "2026-10-29", "2026-11-05", "2026-11-21"),
            store.repetitions.map { it.scheduledOn.toString() }
        )
    }

    @Test
    fun insufficientNotesAreSavedWithoutContext() = runBlocking {
        val store = FakeStore()
        val service = NoteService(store, NoteAnalyzer {
            NoteAnalysis.Insufficient("Not enough explanation")
        }, clock, FixedIntervalSchedulePlanner())
        val result = service.create("Title", "A note")
        assertEquals(NoteStatus.INSUFFICIENT, result.note.status)
        assertEquals("Not enough explanation", result.note.analysisReason)
        assertNull(result.context)
        assertEquals(0, store.repetitions.size)
    }

    @Test
    fun llmFailureDoesNotCreateNote() = runBlocking {
        val store = FakeStore()
        val service = NoteService(store, NoteAnalyzer {
            throw NoteAnalysisException("Unreachable")
        }, clock, FixedIntervalSchedulePlanner())
        assertFailsWith<NoteAnalysisException> { service.create("Title", "A note") }
        assertEquals(0, store.notes.size)
    }

    @Test
    fun blankContentIsRejectedBeforeLlmCall() = runBlocking {
        val store = FakeStore()
        val service = NoteService(store, NoteAnalyzer {
            throw AssertionError("Analyzer should not be called")
        }, clock, FixedIntervalSchedulePlanner())
        assertFailsWith<InvalidNoteException> { service.create("Title", "  ") }
    }
}

private class FakeStore : NoteRepository {
    val notes = mutableMapOf<UUID, Note>()
    val contexts = mutableMapOf<UUID, VerificationContext>()
    val repetitions = mutableListOf<Repetition>()

    override suspend fun saveAnalyzed(note: Note, context: VerificationContext?, repetitions: List<Repetition>) {
        notes[note.id] = note
        if (context != null) contexts[note.id] = context
        this.repetitions.addAll(repetitions)
    }

    override suspend fun findById(id: UUID): Note? = notes[id]
    override suspend fun findAll(): List<Note> = notes.values.toList()
    override suspend fun findContextByNoteId(noteId: UUID): VerificationContext? = contexts[noteId]
}
