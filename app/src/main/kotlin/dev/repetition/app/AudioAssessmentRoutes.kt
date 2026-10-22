package dev.repetition.app

import dev.repetition.assessment.AudioTooLargeException
import dev.repetition.assessment.InvalidAudioException
import dev.repetition.assessment.UnsupportedAudioFormatException
import dev.repetition.assessment.VoiceAssessmentService
import dev.repetition.scheduling.RepetitionService
import dev.repetition.scheduling.RepetitionStatus
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.audioAssessmentRoutes(service: VoiceAssessmentService, repetitions: RepetitionService) {
    post("/repetitions/{id}/assess/audio") {
        val repetitionId = try {
            UUID.fromString(call.parameters["id"] ?: throw ValidationException("Repetition ID is required"))
        } catch (_: IllegalArgumentException) {
            throw ValidationException("Repetition ID must be a valid UUID")
        }
        val contentType = call.request.headers[HttpHeaders.ContentType]
            ?.substringBefore(';')?.trim()?.lowercase()
            ?: throw UnsupportedAudioFormatException("Audio Content-Type is required")
        if (contentType !in VoiceAssessmentService.ALLOWED_AUDIO_TYPES) {
            throw UnsupportedAudioFormatException("Unsupported audio format")
        }
        val declaredLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > VoiceAssessmentService.MAX_AUDIO_BYTES) {
            throw AudioTooLargeException("Audio exceeds 20 MiB")
        }
        val audio = withContext(Dispatchers.IO) {
            call.receiveStream().use { stream ->
                stream.readNBytes(VoiceAssessmentService.MAX_AUDIO_BYTES + 1)
            }
        }
        if (audio.size > VoiceAssessmentService.MAX_AUDIO_BYTES) {
            throw AudioTooLargeException("Audio exceeds 20 MiB")
        }
        if (audio.isEmpty()) throw InvalidAudioException("Audio must not be empty")
        val result = service.submit(repetitionId, audio, contentType)
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
