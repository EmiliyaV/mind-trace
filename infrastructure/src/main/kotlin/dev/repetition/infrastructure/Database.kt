package dev.repetition.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.Contexts
import liquibase.LabelExpression
import javax.sql.DataSource

data class DatabaseSettings(
    val host: String,
    val port: Int,
    val name: String,
    val user: String,
    val password: String
)

class ManagedDataSource(
    private val delegate: DataSource,
    private val closeable: AutoCloseable
) : DataSource by delegate, AutoCloseable {
    override fun close() = closeable.close()
}

object DatabaseFactoryProvider {
    fun create(settings: DatabaseSettings): ManagedDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${settings.host}:${settings.port}/${settings.name}"
            username = settings.user
            password = settings.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 1
            connectionTimeout = 10_000L
            poolName = "repetition-db"
        }
        val pool = HikariDataSource(config)
        return ManagedDataSource(pool, pool)
    }
}

object Migrations {
    fun apply(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog.xml",
                ClassLoaderResourceAccessor(),
                database
            ).use { liquibase ->
                liquibase.update(Contexts(), LabelExpression())
            }
        }
    }
}
