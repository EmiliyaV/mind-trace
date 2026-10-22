package dev.repetition.notes

import java.util.UUID
import dev.repetition.scheduling.Repetition

data class NoteDetails(val note: Note, val context: VerificationContext?)

interface NoteRepository {
    suspend fun saveAnalyzed(note: Note, context: VerificationContext?, repetitions: List<Repetition>)
    suspend fun findById(id: UUID): Note?
    suspend fun findAll(): List<Note>
    suspend fun findContextByNoteId(noteId: UUID): VerificationContext?
}

fun interface NoteAnalyzer {
    suspend fun analyze(content: String): NoteAnalysis
}
