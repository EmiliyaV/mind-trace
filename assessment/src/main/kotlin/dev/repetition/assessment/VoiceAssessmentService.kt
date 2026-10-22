package dev.repetition.assessment

import java.util.UUID

class InvalidAudioException(message: String) : RuntimeException(message)
class UnrecognizableAudioException(message: String) : RuntimeException(message)
class UnsupportedAudioFormatException(message: String) : RuntimeException(message)
class AudioTooLargeException(message: String) : RuntimeException(message)
class TranscriptionExternalException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class VoiceAssessmentService(
    private val assessmentService: AssessmentService,
    private val transcriber: Transcriber
) {
    suspend fun submit(repetitionId: UUID, audio: ByteArray, contentType: String): AssessmentResult {
        if (audio.isEmpty()) throw InvalidAudioException("Audio must not be empty")
        if (audio.size > MAX_AUDIO_BYTES) throw AudioTooLargeException("Audio exceeds 20 MiB")
        if (contentType !in ALLOWED_AUDIO_TYPES) throw UnsupportedAudioFormatException("Unsupported audio format")
        val existing = assessmentService.checkReady(repetitionId)
        if (existing != null) return existing
        val answer = transcriber.transcribe(audio, contentType)
        if (answer.isBlank()) throw UnrecognizableAudioException("Speech was not recognized")
        return assessmentService.submit(repetitionId, answer)
    }

    companion object {
        const val MAX_AUDIO_BYTES = 20 * 1024 * 1024
        val ALLOWED_AUDIO_TYPES = setOf(
            "audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3",
            "audio/webm", "audio/ogg", "audio/mp4", "audio/x-m4a", "audio/flac"
        )
    }
}
