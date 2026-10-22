package dev.repetition.notes

import java.time.Instant
import java.util.UUID

enum class NoteStatus {
    PENDING_REVIEW,
    INSUFFICIENT,
    READY
}

data class Note(
    val id: UUID,
    val title: String,
    val content: String,
    val status: NoteStatus,
    val createdAt: Instant,
    val analysisReason: String? = null
)

data class VerificationAspect(
    val description: String
)

data class VerificationContext(
    val noteId: UUID,
    val aspects: List<VerificationAspect>,
    val createdAt: Instant
)

sealed interface NoteAnalysis {
    data class Sufficient(val aspects: List<VerificationAspect>) : NoteAnalysis
    data class Insufficient(val reason: String) : NoteAnalysis
}
