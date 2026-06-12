package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.DbEnv
import org.flywaydb.core.Flyway
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

const val POSTGRES_JDBC_PREFIX = "jdbc:postgresql"

interface DatabaseInterface {
    val connection: Connection
    val log: Logger
}

class Database(
    val env: DbEnv,
) : DatabaseInterface {
    val hikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = generateJdbcUrlFromEnv(env)
                username = env.dbUsername
                password = env.dbPassword
                maximumPoolSize = 3
                minimumIdle = 1
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                validate()
            },
        )

    init {
        runFlywayMigrations()
    }

    override val connection: Connection
        get() = hikariDataSource.connection

    override val log: Logger = LoggerFactory.getLogger(Database::class.qualifiedName)

    // Flyway runs on its own driver-based connection with autoCommit=true (Flyway's default) so that
    // migrations using CREATE INDEX CONCURRENTLY (which cannot run inside a transaction block) work
    // correctly. The application pool uses autoCommit=false, which would otherwise wrap each statement
    // in an implicit transaction and cause CONCURRENTLY to hang.
    //
    // transactionalLock=false makes Flyway take a session-level advisory lock instead of a
    // transactional one. A transactional lock keeps a connection idle-in-transaction for the whole
    // migration run, and that open snapshot blocks CREATE INDEX CONCURRENTLY indefinitely.
    private fun runFlywayMigrations() =
        Flyway.configure().run {
            dataSource(generateJdbcUrlFromEnv(env), env.dbUsername, env.dbPassword)
            getConfigurationExtension(PostgreSQLConfigurationExtension::class.java).isTransactionalLock = false
            load().migrate().migrationsExecuted
        }
}

fun generateJdbcUrlFromEnv(env: DbEnv): String = "$POSTGRES_JDBC_PREFIX://${env.dbHost}:${env.dbPort}/${env.dbName}"

fun DatabaseInterface.grantAccessToIAMUsers() {
    val statement =
        """
        GRANT ALL ON ALL TABLES IN SCHEMA PUBLIC TO CLOUDSQLIAMUSER
        """.trimIndent()

    connection.use { conn ->
        conn.prepareStatement(statement).use {
            it.executeUpdate()
        }
        conn.commit()
    }
}
