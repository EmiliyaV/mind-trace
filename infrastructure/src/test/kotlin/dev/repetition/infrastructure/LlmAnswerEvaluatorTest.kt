package dev.repetition.infrastructure

import dev.repetition.assessment.AssessmentExternalException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LlmAnswerEvaluatorTest {
    @Test
    fun acceptsValidScoreAndExplanation() {
        val result = parseEvaluation("""{"score":80,"explanation":"Some missing facts"}""")
        assertEquals(80, result.score)
        assertEquals("Some missing facts", result.explanation)
    }

    @Test
    fun rejectsScoreOutsideRange() {
        assertFailsWith<AssessmentExternalException> {
            parseEvaluation("""{"score":101,"explanation":"Invalid"}""")
        }
    }

    @Test
    fun rejectsNonIntegerScore() {
        assertFailsWith<AssessmentExternalException> {
            parseEvaluation("""{"score":78.5,"explanation":"Invalid"}""")
        }
    }

    @Test
    fun rejectsBlankExplanation() {
        assertFailsWith<AssessmentExternalException> {
            parseEvaluation("""{"score":80,"explanation":"  "}""")
        }
    }
}
