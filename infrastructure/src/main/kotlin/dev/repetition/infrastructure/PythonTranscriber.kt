package dev.repetition.infrastructure

import dev.repetition.assessment.AudioTooLargeException
import dev.repetition.assessment.InvalidAudioException
import dev.repetition.assessment.Transcriber
import dev.repetition.assessment.TranscriptionExternalException
import dev.repetition.assessment.UnsupportedAudioFormatException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class TranscriptionSettings(val baseUrl: String, val timeoutMs: Long)

class PythonTranscriber(private val settings: TranscriptionSettings) : Transcriber, AutoCloseable {
    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = settings.timeoutMs
            connectTimeoutMillis = minOf(settings.timeoutMs, 10_000L)
            socketTimeoutMillis = settings.timeoutMs
        }
    }

    override suspend fun transcribe(audio: ByteArray, contentType: String): String {
        try {
            val response = client.post(settings.baseUrl.trimEnd('/') + "/transcribe") {
                contentType(ContentType.parse(contentType))
                setBody(audio)
            }
            when (response.status) {
                HttpStatusCode.UnsupportedMediaType -> throw UnsupportedAudioFormatException("Unsupported audio format")
                HttpStatusCode.PayloadTooLarge -> throw AudioTooLargeException("Audio exceeds 20 MiB")
                HttpStatusCode.UnprocessableEntity -> throw UnrecognizableAudioException("Audio cannot be decoded or speech was not recognized")
                HttpStatusCode.BadRequest -> throw InvalidAudioException("Invalid audio request")
                HttpStatusCode.OK -> Unit
                else -> throw TranscriptionExternalException("Transcription service returned HTTP ${response.status.value}")
            }
            val text = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                .getValue("text").jsonPrimitive.content.trim()
            if (text.isBlank()) throw UnrecognizableAudioException("Speech was not recognized")
            return text
        } catch (error: CancellationException) {
            throw error
        } catch (error: UnrecognizableAudioException) {
            throw error
        } catch (error: InvalidAudioException) {
            throw error
        } catch (error: AudioTooLargeException) {
            throw error
        } catch (error: UnsupportedAudioFormatException) {
            throw error
        } catch (error: TranscriptionExternalException) {
            throw error
        } catch (error: Exception) {
            throw TranscriptionExternalException("Transcription service request failed", error)
        }
    }

    override fun close() = client.close()
}
