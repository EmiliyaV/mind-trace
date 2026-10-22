package dev.repetition.notes

import dev.repetition.scheduling.SchedulePlanner
import java.time.Clock
import java.time.Instant
import java.util.UUID

class InvalidNoteException(message: String) : RuntimeException(message)

class NoteAnalysisException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class NoteService(
    private val repository: NoteRepository,
    private val analyzer: NoteAnalyzer,
    private val clock: Clock,
    private val planner: SchedulePlanner
) {
    suspend fun create(title: String, content: String): NoteDetails {
        val normalizedTitle = title.trim()
        val normalizedContent = content.trim()
        if (normalizedTitle.isEmpty() || normalizedTitle.length > 255) {
            throw InvalidNoteException("Title must contain 1 to 255 characters")
        }
        if (normalizedContent.isEmpty() || normalizedContent.length > 30_000) {
            throw InvalidNoteException("Content must contain 1 to 30000 characters")
        }

        val analysis = analyzer.analyze(normalizedContent)
        val now = Instant.now(clock)
        val note = Note(
            id = UUID.randomUUID(),
            title = normalizedTitle,
            content = normalizedContent,
            status = when (analysis) {
                is NoteAnalysis.Sufficient -> NoteStatus.READY
                is NoteAnalysis.Insufficient -> NoteStatus.INSUFFICIENT
            },
            createdAt = now,
            analysisReason = (analysis as? NoteAnalysis.Insufficient)?.reason
        )
        val context = (analysis as? NoteAnalysis.Sufficient)?.let {
            VerificationContext(note.id, it.aspects, now)
        }
        val repetitions = if (context == null) emptyList() else {
            planner.generate(note.id, now.atZone(clock.zone).toLocalDate())
        }
        repository.saveAnalyzed(note, context, repetitions)
        return NoteDetails(note, context)
    }

    suspend fun findAll(): List<Note> = repository.findAll()

    suspend fun findById(id: UUID): NoteDetails? = repository.findById(id)?.let { note ->
        NoteDetails(note, repository.findContextByNoteId(id))
    }
}
