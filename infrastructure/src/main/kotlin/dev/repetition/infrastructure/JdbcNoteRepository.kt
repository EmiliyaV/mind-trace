package dev.repetition.infrastructure

import dev.repetition.scheduling.Repetition
import dev.repetition.scheduling.RepetitionStatus
import dev.repetition.notes.Note
import dev.repetition.notes.NoteRepository
import dev.repetition.notes.NoteStatus
import dev.repetition.notes.VerificationAspect
import dev.repetition.notes.VerificationContext
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JdbcNoteRepository(private val dataSource: DataSource) : NoteRepository {
    override suspend fun saveAnalyzed(
        note: Note, context: VerificationContext?, repetitions: List<Repetition>
    ) = withContext(Dispatchers.IO) {
        require((note.status == NoteStatus.READY) == (context != null))
        require((context != null) == (repetitions.isNotEmpty()))
        require(repetitions.all {
            it.noteId == note.id && it.status == RepetitionStatus.PENDING && it.completedAt == null
        })
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "INSERT INTO notes (id, title, content, status, created_at, analysis_reason) VALUES (?, ?, ?, ?, ?, ?)"
                ).use { statement ->
                    statement.setObject(1, note.id)
                    statement.setString(2, note.title)
                    statement.setString(3, note.content)
                    statement.setString(4, note.status.name)
                    statement.setTimestamp(5, Timestamp.from(note.createdAt))
                    statement.setString(6, note.analysisReason)
                    statement.executeUpdate()
                }
                if (context != null) {
                    require(context.noteId == note.id)
                    val json = JsonArray(context.aspects.map { aspect ->
                        JsonObject(mapOf("description" to JsonPrimitive(aspect.description)))
                    }).toString()
                    connection.prepareStatement(
                        "INSERT INTO verification_contexts (note_id, aspects, created_at) VALUES (?, ?::jsonb, ?)"
                    ).use { statement ->
                        statement.setObject(1, note.id)
                        statement.setString(2, json)
                        statement.setTimestamp(3, Timestamp.from(context.createdAt))
                        statement.executeUpdate()
                    }
                }
                if (repetitions.isNotEmpty()) {
                    connection.prepareStatement(
                        "INSERT INTO repetitions (id, note_id, scheduled_on, status, completed_at) " +
                            "VALUES (?, ?, ?, ?, ?)"
                    ).use { statement ->
                        repetitions.forEach { repetition ->
                            statement.setObject(1, repetition.id)
                            statement.setObject(2, repetition.noteId)
                            statement.setObject(3, repetition.scheduledOn)
                            statement.setString(4, repetition.status.name)
                            statement.setTimestamp(5, null)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    }

    override suspend fun findById(id: UUID): Note? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, title, content, status, created_at, analysis_reason FROM notes WHERE id = ?"
            ).use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { rows -> if (rows.next()) rows.toNote() else null }
            }
        }
    }

    override suspend fun findAll(): List<Note> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, title, content, status, created_at, analysis_reason FROM notes ORDER BY created_at DESC, id DESC"
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.toNote()) }
                }
            }
        }
    }

    override suspend fun findContextByNoteId(noteId: UUID): VerificationContext? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT note_id, aspects, created_at FROM verification_contexts WHERE note_id = ?"
            ).use { statement ->
                statement.setObject(1, noteId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) null else {
                        val aspects = Json.parseToJsonElement(rows.getString("aspects")).jsonArray.map { item ->
                            VerificationAspect(item.jsonObject.getValue("description").jsonPrimitive.content)
                        }
                        VerificationContext(
                            rows.getObject("note_id", UUID::class.java),
                            aspects,
                            rows.getTimestamp("created_at").toInstant()
                        )
                    }
                }
            }
        }
    }

    private fun ResultSet.toNote() = Note(
        id = getObject("id", UUID::class.java),
        title = getString("title"),
        content = getString("content"),
        status = NoteStatus.valueOf(getString("status")),
        createdAt = getTimestamp("created_at").toInstant(),
        analysisReason = getString("analysis_reason")
    )
}
