package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.DbEnvironment
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.util.*

interface DatabaseInterface {
    val connection: Connection
}

enum class Role {
    ADMIN, USER;

    override fun toString() = name.lowercase(Locale.getDefault())
}

/**
 * Base Database implementation.
 * Hooks up the database with the provided configuration/credentials
 */
class Database(val env: DbEnvironment) : DatabaseInterface {

    private var hikariDataSource: HikariDataSource
    val hikariConfig: HikariConfig = HikariConfig().apply {
        jdbcUrl = generateJdbcUrlFromEnv()
        maximumPoolSize = 2
        minimumIdle = 1
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    init {
        hikariDataSource = HikariDataSource(hikariConfig.apply {
            username = env.dbUsername
            password = env.dbPassword
        })
        runFlywayMigrations(hikariDataSource)
    }

    override val connection: Connection
        get() = hikariDataSource.connection


    private fun runFlywayMigrations(hikariDataSource: HikariDataSource) = Flyway.configure().run {
        dataSource(hikariDataSource)
        initSql("SET ROLE \"${env.dbName}-${Role.ADMIN}\"") //
        load().migrate().migrationsExecuted
    }

    private fun generateJdbcUrlFromEnv(): String {
        return "jdbc:postgresql://${env.dbHost}:${env.dbPort}/${env.dbName}"
    }
}
