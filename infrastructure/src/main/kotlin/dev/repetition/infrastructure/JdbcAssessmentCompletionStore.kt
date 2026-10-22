package dev.repetition.infrastructure

import dev.repetition.assessment.Assessment
import dev.repetition.assessment.AssessmentCompletionStore
import dev.repetition.assessment.AssessmentConflictException
import dev.repetition.assessment.AssessmentNotFoundException
import dev.repetition.assessment.Evaluation
import dev.repetition.scheduling.AdaptiveSchedulePlanner
import dev.repetition.scheduling.Repetition
import dev.repetition.scheduling.RepetitionStatus
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JdbcAssessmentCompletionStore(
    private val dataSource: DataSource,
    private val planner: AdaptiveSchedulePlanner,
    private val zone: ZoneId
) : AssessmentCompletionStore {
    override suspend fun findByRepetitionId(repetitionId: UUID): Assessment? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection -> readAssessment(connection, repetitionId) }
    }

    override suspend fun completeAtomically(assessment: Assessment): Assessment = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val noteLocked = connection.prepareStatement(
                    "SELECT id FROM notes WHERE id = ? FOR UPDATE"
                ).use { statement ->
                    statement.setObject(1, assessment.noteId)
                    statement.executeQuery().use { it.next() }
                }
                if (!noteLocked) throw AssessmentNotFoundException("Note not found")

                val existing = readAssessment(connection, assessment.repetitionId)
                if (existing != null) {
                    connection.commit()
                    return@withContext existing
                }

                val repetition = connection.prepareStatement(
                    "SELECT id, note_id, scheduled_on, status, completed_at " +
                        "FROM repetitions WHERE id = ? FOR UPDATE"
                ).use { statement ->
                    statement.setObject(1, assessment.repetitionId)
                    statement.executeQuery().use { rows -> if (rows.next()) rows.toRepetition() else null }
                } ?: throw AssessmentNotFoundException("Repetition not found")
                if (repetition.noteId != assessment.noteId) {
                    throw AssessmentConflictException("Repetition belongs to a different note")
                }
                if (repetition.status != RepetitionStatus.PENDING) {
                    throw AssessmentConflictException("Repetition is not pending")
                }

                val pending = connection.prepareStatement(
                    "SELECT id, note_id, scheduled_on, status, completed_at FROM repetitions " +
                        "WHERE note_id = ? AND status = 'PENDING' ORDER BY scheduled_on, id FOR UPDATE"
                ).use { statement ->
                    statement.setObject(1, assessment.noteId)
                    statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toRepetition()) } }
                }
                if (pending.firstOrNull()?.id != repetition.id) {
                    throw AssessmentConflictException("Complete the earliest pending repetition first")
                }
                val actualDate = assessment.evaluatedAt.atZone(zone).toLocalDate()
                if (actualDate.isBefore(repetition.scheduledOn)) {
                    throw AssessmentConflictException("Repetition is not due yet")
                }

                val change = planner.adjust(repetition, pending.drop(1), assessment.evaluation.score, actualDate)
                connection.prepareStatement(
                    "UPDATE repetitions SET status = 'COMPLETED', completed_at = ? " +
                        "WHERE id = ? AND status = 'PENDING'"
                ).use { statement ->
                    statement.setTimestamp(1, Timestamp.from(assessment.evaluatedAt))
                    statement.setObject(2, repetition.id)
                    if (statement.executeUpdate() != 1) {
                        throw AssessmentConflictException("Repetition has already changed")
                    }
                }
                if (change.modified.isNotEmpty()) {
                    connection.prepareStatement(
                        "UPDATE repetitions SET scheduled_on = ?, status = ? " +
                            "WHERE id = ? AND status = 'PENDING'"
                    ).use { statement ->
                        change.modified.forEach { row ->
                            statement.setObject(1, row.scheduledOn)
                            statement.setString(2, row.status.name)
                            statement.setObject(3, row.id)
                            statement.addBatch()
                        }
                        val counts = statement.executeBatch()
                        if (counts.any { it == 0 }) {
                            throw AssessmentConflictException("A scheduled repetition has changed")
                        }
                    }
                }
                if (change.created.isNotEmpty()) {
                    connection.prepareStatement(
                        "INSERT INTO repetitions (id, note_id, scheduled_on, status, completed_at) " +
                            "VALUES (?, ?, ?, ?, NULL)"
                    ).use { statement ->
                        change.created.forEach { row ->
                            statement.setObject(1, row.id)
                            statement.setObject(2, row.noteId)
                            statement.setObject(3, row.scheduledOn)
                            statement.setString(4, row.status.name)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
                connection.prepareStatement(
                    "INSERT INTO assessments (id, note_id, repetition_id, transcription, score, explanation, evaluated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)"
                ).use { statement ->
                    statement.setObject(1, assessment.id)
                    statement.setObject(2, assessment.noteId)
                    statement.setObject(3, assessment.repetitionId)
                    statement.setString(4, assessment.transcription)
                    statement.setInt(5, assessment.evaluation.score)
                    statement.setString(6, assessment.evaluation.explanation)
                    statement.setTimestamp(7, Timestamp.from(assessment.evaluatedAt))
                    statement.executeUpdate()
                }
                connection.commit()
                assessment
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    }

    private fun readAssessment(connection: Connection, repetitionId: UUID): Assessment? =
        connection.prepareStatement(
            "SELECT id, note_id, repetition_id, transcription, score, explanation, evaluated_at " +
                "FROM assessments WHERE repetition_id = ?"
        ).use { statement ->
            statement.setObject(1, repetitionId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toAssessment() else null }
        }

    private fun ResultSet.toRepetition(): Repetition = Repetition(
        id = getObject("id", UUID::class.java),
        noteId = getObject("note_id", UUID::class.java),
        scheduledOn = getObject("scheduled_on", LocalDate::class.java),
        status = RepetitionStatus.valueOf(getString("status")),
        completedAt = getTimestamp("completed_at")?.toInstant()
    )

    private fun ResultSet.toAssessment(): Assessment = Assessment(
        id = getObject("id", UUID::class.java),
        noteId = getObject("note_id", UUID::class.java),
        repetitionId = getObject("repetition_id", UUID::class.java),
        transcription = getString("transcription"),
        evaluation = Evaluation(getInt("score"), getString("explanation")),
        evaluatedAt = getTimestamp("evaluated_at").toInstant()
    )
}
