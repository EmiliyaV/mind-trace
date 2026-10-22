package dev.repetition.app

import dev.repetition.notes.Note
import dev.repetition.notes.NoteDetails
import dev.repetition.notes.NoteService
import dev.repetition.notes.VerificationContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class CreateNoteRequest(val title: String, val content: String)

@Serializable
data class AspectResponse(val description: String)

@Serializable
data class VerificationContextResponse(val aspects: List<AspectResponse>)

@Serializable
data class NoteResponse(
    val id: String,
    val title: String,
    val content: String,
    val status: String,
    val createdAt: String,
    val analysisReason: String?,
    val context: VerificationContextResponse?
)

private fun VerificationContext.toResponse() = VerificationContextResponse(
    aspects.map { AspectResponse(it.description) }
)

private fun Note.toResponse(context: VerificationContext? = null) = NoteResponse(
    id = id.toString(),
    title = title,
    content = content,
    status = status.name,
    createdAt = createdAt.toString(),
    analysisReason = analysisReason,
    context = context?.toResponse()
)

private fun NoteDetails.toResponse() = note.toResponse(context)

fun Route.noteRoutes(service: NoteService) {
    route("/notes") {
        post {
            val request = try {
                call.receive<CreateNoteRequest>()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                throw ValidationException("Invalid JSON request body")
            }
            val created = service.create(request.title, request.content)
            call.application.log.info("Created note id={} status={}", created.note.id, created.note.status)
            call.respond(HttpStatusCode.Created, created.toResponse())
        }
        get {
            call.respond(service.findAll().map { it.toResponse() })
        }
        get("/{id}") {
            val rawId = call.parameters["id"] ?: throw ValidationException("Note ID is required")
            val id = try {
                UUID.fromString(rawId)
            } catch (_: IllegalArgumentException) {
                throw ValidationException("Note ID must be a valid UUID")
            }
            val note = service.findById(id) ?: throw NotFoundException("Note not found")
            call.respond(note.toResponse())
        }
    }
}
