package dev.repetition.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

class DatabaseHealth(private val dataSource: DataSource) {
    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT 1").use { statement ->
                    statement.executeQuery().use { result ->
                        result.next() && result.getInt(1) == 1
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
