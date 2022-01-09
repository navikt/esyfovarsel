package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.DbEnv
import org.flywaydb.core.Flyway
import java.sql.Connection

const val postgresJdbcPrefix = "jdbc:postgresql"

interface DatabaseInterface {
    val connection: Connection
}

/**
 * Base Database implementation.
 * Hooks up the database with the provided configuration/credentials
 */
class Database(val env: DbEnv) : DatabaseInterface {
    val hikariDataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = generateJdbcUrlFromEnv()
        username = env.dbUsername
        password = env.dbPassword
        maximumPoolSize = 2
        minimumIdle = 1
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    })

    init {
        runFlywayMigrations(hikariDataSource)
    }

    override val connection: Connection
        get() = hikariDataSource.connection


    private fun runFlywayMigrations(hikariDataSource: HikariDataSource) = Flyway.configure().run {
        dataSource(hikariDataSource)
        load().migrate().migrationsExecuted
    }

    private fun generateJdbcUrlFromEnv(): String {
        return "$postgresJdbcPrefix://${env.dbHost}:${env.dbPort}/${env.dbName}"
    }
}
