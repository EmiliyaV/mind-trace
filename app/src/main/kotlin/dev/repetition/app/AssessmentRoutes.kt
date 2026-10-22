package dev.repetition.app

import dev.repetition.assessment.AssessmentService
import dev.repetition.scheduling.RepetitionService
import dev.repetition.scheduling.RepetitionStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class EvaluateAnswerRequest(val answer: String)

@Serializable
data class EvaluateAnswerResponse(
    val id: String,
    val noteId: String,
    val repetitionId: String,
    val score: Int,
    val explanation: String,
    val evaluatedAt: String,
    val nextScheduledOn: String?,
    val alreadyCompleted: Boolean
)

fun Route.assessmentRoutes(service: AssessmentService, repetitions: RepetitionService) {
    post("/repetitions/{id}/assess") {
        val rawId = call.parameters["id"] ?: throw ValidationException("Repetition ID is required")
        val repetitionId = try {
            UUID.fromString(rawId)
        } catch (_: IllegalArgumentException) {
            throw ValidationException("Repetition ID must be a valid UUID")
        }
        val request = try {
            call.receive<EvaluateAnswerRequest>()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw ValidationException("Invalid JSON request body")
        }
        val result = service.submit(repetitionId, request.answer)
        val assessment = result.assessment
        val next = repetitions.findSchedule(assessment.noteId)
            .filter { it.status == RepetitionStatus.PENDING }
            .minByOrNull { it.scheduledOn }?.scheduledOn
        call.respond(
            HttpStatusCode.OK,
            EvaluateAnswerResponse(
                id = assessment.id.toString(),
                noteId = assessment.noteId.toString(),
                repetitionId = assessment.repetitionId.toString(),
                score = assessment.evaluation.score,
                explanation = assessment.evaluation.explanation,
                evaluatedAt = assessment.evaluatedAt.toString(),
                nextScheduledOn = next?.toString(),
                alreadyCompleted = result.replayed
            )
        )
    }
}
