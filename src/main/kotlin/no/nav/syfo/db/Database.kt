package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.ResultSet


data class DbConfig(
    val jdbcUrl: String,
    val password: String,
    val username: String,
    val databaseName: String,
    val poolSize: Int = 2,
    val runMigrationsOninit: Boolean = true
)

class LocalDatabase(daoConfig: DbConfig) : Database(daoConfig, null)

class RemoteDatabase(daoConfig: DbConfig, initBlock: (context: Database) -> Unit) : Database(daoConfig, initBlock) {

    override fun runFlywayMigrations(jdbcUrl: String, username: String, password: String): Int = Flyway.configure().run {
        dataSource(jdbcUrl, username, password)
        initSql("SET ROLE \"${daoConfig.databaseName}-admin") // required for assigning proper owners for the tables
        load().migrate().migrationsExecuted
    }
}

/**
 * Base Database implementation.
 * Hooks up the database with the provided configuration/credentials
 */
abstract class Database(val daoConfig: DbConfig, private val initBlock: ((context: Database) -> Unit)?) : DatabaseInterface {

    var dataSource: HikariDataSource

    init {

        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = daoConfig.jdbcUrl
            username = daoConfig.username
            password = daoConfig.password
            maximumPoolSize = daoConfig.poolSize
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        })

        afterInit()
    }

    override val connection: Connection
        get() = dataSource.connection

    private fun afterInit() {
        if (daoConfig.runMigrationsOninit) {
            runFlywayMigrations(daoConfig.jdbcUrl, daoConfig.username, daoConfig.password)
        }
        initBlock?.let { run(it) }
    }

    open fun runFlywayMigrations(jdbcUrl: String, username: String, password: String) = Flyway.configure().run {
        dataSource(jdbcUrl, username, password)
        load().migrate().migrationsExecuted
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

interface DatabaseInterface {
    val connection: Connection
}
