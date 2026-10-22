package dev.repetition.app

import dev.repetition.notes.NoteService
import dev.repetition.scheduling.Repetition
import dev.repetition.scheduling.RepetitionService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class RepetitionResponse(
    val id: String,
    val noteId: String,
    val scheduledOn: String,
    val status: String,
    val completedAt: String?
)

@Serializable
data class ScheduleResponse(
    val noteId: String,
    val repetitions: List<RepetitionResponse>
)

@Serializable
data class DueRepetitionsResponse(
    val asOfDate: String,
    val dueToday: List<RepetitionResponse>,
    val overdue: List<RepetitionResponse>
)

private fun Repetition.toResponse() = RepetitionResponse(
    id = id.toString(),
    noteId = noteId.toString(),
    scheduledOn = scheduledOn.toString(),
    status = status.name,
    completedAt = completedAt?.toString()
)

fun Route.repetitionRoutes(noteService: NoteService, service: RepetitionService) {
    route("/notes/{id}/schedule") {
        get {
            val rawId = call.parameters["id"] ?: throw ValidationException("Note ID is required")
            val noteId = try {
                UUID.fromString(rawId)
            } catch (_: IllegalArgumentException) {
                throw ValidationException("Note ID must be a valid UUID")
            }
            if (noteService.findById(noteId) == null) throw NotFoundException("Note not found")
            call.respond(
                ScheduleResponse(noteId.toString(), service.findSchedule(noteId).map { it.toResponse() })
            )
        }
    }
    get("/repetitions/due") {
        val due = service.findDue()
        call.respond(
            DueRepetitionsResponse(
                asOfDate = due.asOfDate.toString(),
                dueToday = due.dueToday.map { it.toResponse() },
                overdue = due.overdue.map { it.toResponse() }
            )
        )
    }
}
