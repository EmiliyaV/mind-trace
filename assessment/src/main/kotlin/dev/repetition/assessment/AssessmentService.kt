package dev.repetition.assessment

import dev.repetition.notes.NoteRepository
import dev.repetition.notes.NoteStatus
import dev.repetition.scheduling.RepetitionRepository
import dev.repetition.scheduling.RepetitionStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InvalidAnswerException(message: String) : RuntimeException(message)
class AssessmentNotFoundException(message: String) : RuntimeException(message)
class AssessmentConflictException(message: String) : RuntimeException(message)
class AssessmentExternalException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class AssessmentResult(val assessment: Assessment, val replayed: Boolean)

class AssessmentService(
    private val notes: NoteRepository,
    private val repetitions: RepetitionRepository,
    private val completionStore: AssessmentCompletionStore,
    private val evaluator: AnswerEvaluator,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend fun checkReady(repetitionId: UUID): AssessmentResult? {
        val existing = completionStore.findByRepetitionId(repetitionId)
        if (existing != null) return AssessmentResult(existing, true)
        val repetition = repetitions.findById(repetitionId)
            ?: throw AssessmentNotFoundException("Repetition not found")
        if (repetition.status != RepetitionStatus.PENDING) {
            throw AssessmentConflictException("Repetition is not pending")
        }
        if (repetition.scheduledOn.isAfter(LocalDate.now(clock))) {
            throw AssessmentConflictException("Repetition is not due yet")
        }
        val earliest = repetitions.findByNoteId(repetition.noteId)
            .filter { it.status == RepetitionStatus.PENDING }
            .minWithOrNull(compareBy({ it.scheduledOn }, { it.id }))
        if (earliest?.id != repetitionId) {
            throw AssessmentConflictException("Complete the earliest pending repetition first")
        }
        val note = notes.findById(repetition.noteId)
            ?: throw AssessmentNotFoundException("Note not found")
        if (note.status != NoteStatus.READY) {
            throw AssessmentConflictException("Note is not ready for assessment")
        }
        if (notes.findContextByNoteId(repetition.noteId) == null) {
            throw AssessmentConflictException("Verification context is missing")
        }
        return null
    }

    suspend fun submit(repetitionId: UUID, answer: String): AssessmentResult {
        val previous = checkReady(repetitionId)
        if (previous != null) return previous
        val normalized = answer.trim()
        if (normalized.isBlank() || normalized.length > 30_000) {
            throw InvalidAnswerException("Answer must contain 1 to 30000 characters")
        }
        val repetition = repetitions.findById(repetitionId)
            ?: throw AssessmentNotFoundException("Repetition not found")
        val context = notes.findContextByNoteId(repetition.noteId)
            ?: throw AssessmentConflictException("Verification context is missing")

        val evaluation = evaluator.evaluate(normalized, context)
        val assessment = Assessment(
            id = UUID.randomUUID(),
            noteId = repetition.noteId,
            repetitionId = repetitionId,
            transcription = normalized,
            evaluation = evaluation,
            evaluatedAt = Instant.now(clock)
        )
        val saved = completionStore.completeAtomically(assessment)
        return AssessmentResult(saved, saved.id != assessment.id)
    }
}
