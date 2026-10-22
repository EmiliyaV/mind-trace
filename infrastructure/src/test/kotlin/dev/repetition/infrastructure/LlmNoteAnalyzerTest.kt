package dev.repetition.infrastructure

import dev.repetition.notes.NoteAnalysis
import dev.repetition.notes.NoteAnalysisException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LlmNoteAnalyzerTest {
    @Test
    fun sufficientAnalysisContainsAspects() {
        val result = parseNoteAnalysis("""{"sufficient":true,"reason":null,"aspects":[{"description":"Transactions"}]}""")
        val sufficient = assertIs<NoteAnalysis.Sufficient>(result)
        assertEquals("Transactions", sufficient.aspects.single().description)
    }

    @Test
    fun insufficientAnalysisContainsReason() {
        val result = parseNoteAnalysis("""{"sufficient":false,"reason":"Only keywords","aspects":[]}""")
        assertEquals("Only keywords", assertIs<NoteAnalysis.Insufficient>(result).reason)
    }

    @Test
    fun invalidEmptyContextIsRejected() {
        assertFailsWith<NoteAnalysisException> {
            parseNoteAnalysis("""{"sufficient":true,"reason":null,"aspects":[]}""")
        }
    }
}
