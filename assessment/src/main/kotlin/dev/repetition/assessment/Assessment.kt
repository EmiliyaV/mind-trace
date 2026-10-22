package dev.repetition.assessment

import dev.repetition.notes.VerificationContext
import dev.repetition.scheduling.Repetition
import java.time.Instant
import java.util.UUID

data class Evaluation(
    val score: Int,
    val explanation: String
) {
    init {
        require(score in 0..100)
    }
}

data class Assessment(
    val id: UUID,
    val noteId: UUID,
    val repetitionId: UUID,
    val transcription: String,
    val evaluation: Evaluation,
    val evaluatedAt: Instant
)

fun interface AnswerEvaluator {
    suspend fun evaluate(transcription: String, context: VerificationContext): Evaluation
}

interface Transcriber {
    suspend fun transcribe(audio: ByteArray, contentType: String): String
}

interface AssessmentCompletionStore {
    suspend fun findByRepetitionId(repetitionId: UUID): Assessment?
    suspend fun completeAtomically(assessment: Assessment): Assessment
}
