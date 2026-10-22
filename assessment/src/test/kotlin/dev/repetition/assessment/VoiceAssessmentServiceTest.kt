package dev.repetition.assessment

import dev.repetition.notes.Note
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

class VoiceAssessmentServiceTest {
    private val timestamp = Instant.parse("2026-10-22T12:00:00Z")
    private val noteId = UUID.randomUUID()
    private val repetitionId = UUID.randomUUID()
    private val note = Note(noteId, "ACID", "Properties of a transaction", NoteStatus.READY, timestamp)
    private val context = VerificationContext(noteId, listOf(VerificationAspect("Describe ACID")), timestamp)

    @Test
    fun successfulAudioUsesExistingAssessmentPipeline() = runBlocking {
        val fixture = create(LocalDate.of(2026, 10, 21))
        val first = fixture.voice.submit(repetitionId, byteArrayOf(1, 2, 3), "audio/wav")
        assertEquals(86, first.assessment.evaluation.score)
        assertEquals("A spoken explanation", first.assessment.transcription)
        assertEquals(1, fixture.transcriberCalls())
        assertEquals(1, fixture.evaluatorCalls())
        assertEquals(1, fixture.store.completions)
        val replay = fixture.voice.submit(repetitionId, byteArrayOf(9), "audio/wav")
        assertTrue(replay.replayed)
        assertEquals(first.assessment.id, replay.assessment.id)
        assertEquals(1, fixture.transcriberCalls())
        assertEquals(1, fixture.evaluatorCalls())
        assertEquals(1, fixture.store.completions)
    }

    @Test
    fun futureRepetitionDoesNotTranscribe() = runBlocking {
        val fixture = create(LocalDate.of(2026, 10, 23))
        assertFailsWith<AssessmentConflictException> {
            fixture.voice.submit(repetitionId, byteArrayOf(1), "audio/wav")
        }
        assertEquals(0, fixture.transcriberCalls())
    }

    @Test
    fun badAudioIsRejectedBeforeTranscription() = runBlocking {
        val fixture = create(LocalDate.of(2026, 10, 21))
        assertFailsWith<InvalidAudioException> {
            fixture.voice.submit(repetitionId, byteArrayOf(), "audio/wav")
        }
        assertFailsWith<UnsupportedAudioFormatException> {
            fixture.voice.submit(repetitionId, byteArrayOf(1), "text/plain")
        }
        assertEquals(0, fixture.transcriberCalls())
        assertEquals(0, fixture.store.completions)
    }

    private fun create(date: LocalDate): Fixture {
        val store = CompletionStore()
        var transcriberCalls = 0
        var evaluatorCalls = 0
        val transcript = object : Transcriber {
            override suspend fun transcribe(audio: ByteArray, contentType: String): String {
                transcriberCalls++
                return "A spoken explanation"
            }
        }
        val evaluator = AnswerEvaluator { _, _ ->
            evaluatorCalls++
            Evaluation(86, "Well covered")
        }
        val assessment = AssessmentService(
            Notes(note, context), Repetitions(Repetition(repetitionId, noteId, date, RepetitionStatus.PENDING, null)),
            store, evaluator, Clock.fixed(timestamp, ZoneOffset.UTC)
        )
        return Fixture(VoiceAssessmentService(assessment, transcript), store, { transcriberCalls }, { evaluatorCalls })
    }

    private data class Fixture(
        val voice: VoiceAssessmentService,
        val store: CompletionStore,
        val transcriberCalls: () -> Int,
        val evaluatorCalls: () -> Int
    )
}

private class CompletionStore : AssessmentCompletionStore {
    var completions = 0
    private var saved: Assessment? = null
    override suspend fun findByRepetitionId(repetitionId: UUID): Assessment? =
        saved?.takeIf { it.repetitionId == repetitionId }
    override suspend fun completeAtomically(assessment: Assessment): Assessment {
        completions++
        saved = assessment
        return assessment
    }
}

private class Notes(private val note: Note, private val context: VerificationContext) : NoteRepository {
    override suspend fun saveAnalyzed(
        note: Note,
        context: VerificationContext?,
        repetitions: List<Repetition>
    ) = Unit
    override suspend fun findById(id: UUID) = note.takeIf { it.id == id }
    override suspend fun findAll() = listOf(note)
    override suspend fun findContextByNoteId(noteId: UUID) = context.takeIf { it.noteId == noteId }
}

private class Repetitions(private val repetition: Repetition) : RepetitionRepository {
    override suspend fun findById(id: UUID) = repetition.takeIf { it.id == id }
    override suspend fun findByNoteId(noteId: UUID) = listOf(repetition).filter { it.noteId == noteId }
    override suspend fun findDue(onDate: LocalDate) = listOf(repetition).filter { it.scheduledOn <= onDate }
    override suspend fun saveInitial(noteId: UUID, repetitions: List<Repetition>) = Unit
}
