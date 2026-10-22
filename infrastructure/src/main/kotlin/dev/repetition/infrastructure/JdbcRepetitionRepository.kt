package dev.repetition.infrastructure

import dev.repetition.scheduling.Repetition
import dev.repetition.scheduling.RepetitionRepository
import dev.repetition.scheduling.RepetitionStatus
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JdbcRepetitionRepository(private val dataSource: DataSource) : RepetitionRepository {
    override suspend fun findById(id: UUID): Repetition? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, note_id, scheduled_on, status, completed_at FROM repetitions WHERE id = ?"
            ).use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { rows -> if (rows.next()) rows.toRepetition() else null }
            }
        }
    }

    override suspend fun findByNoteId(noteId: UUID): List<Repetition> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, note_id, scheduled_on, status, completed_at " +
                    "FROM repetitions WHERE note_id = ? ORDER BY scheduled_on, id"
            ).use { statement ->
                statement.setObject(1, noteId)
                statement.executeQuery().use { rows -> rows.toRepetitions() }
            }
        }
    }

    override suspend fun findDue(onDate: LocalDate): List<Repetition> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, note_id, scheduled_on, status, completed_at " +
                    "FROM repetitions WHERE status = 'PENDING' AND scheduled_on <= ? " +
                    "ORDER BY scheduled_on, id"
            ).use { statement ->
                statement.setObject(1, onDate)
                statement.executeQuery().use { rows -> rows.toRepetitions() }
            }
        }
    }

    override suspend fun saveInitial(noteId: UUID, repetitions: List<Repetition>) =
        withContext(Dispatchers.IO) {
            require(repetitions.all {
                it.noteId == noteId && it.status == RepetitionStatus.PENDING && it.completedAt == null
            })
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
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
                    connection.commit()
                } catch (error: Exception) {
                    connection.rollback()
                    throw error
                }
            }
        }

    private fun ResultSet.toRepetitions(): List<Repetition> =
        buildList { while (next()) add(toRepetition()) }

    private fun ResultSet.toRepetition(): Repetition = Repetition(
        id = getObject("id", UUID::class.java),
        noteId = getObject("note_id", UUID::class.java),
        scheduledOn = getObject("scheduled_on", LocalDate::class.java),
        status = RepetitionStatus.valueOf(getString("status")),
        completedAt = getTimestamp("completed_at")?.toInstant()
    )
}
