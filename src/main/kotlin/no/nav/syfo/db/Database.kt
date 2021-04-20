package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

interface DatabaseInterface {
    val connection: Connection
}

enum class Role {
    ADMIN, USER;

    override fun toString() = name.toLowerCase()
}

data class DbConfig(
    val jdbcUrl: String,
    val databaseName: String,
    val username: String = "",
    val password: String = "",
    val dbCredMountPath: String = "",
    val poolSize: Int = 2,
    val remote: Boolean = true
)

class LocalDatabase(daoConfig: DbConfig) : Database(daoConfig)

class RemoteDatabase(daoConfig: DbConfig) : Database(daoConfig)

/**
 * Base Database implementation.
 * Hooks up the database with the provided configuration/credentials
 */
abstract class Database(val daoConfig: DbConfig) : DatabaseInterface {

    var hikariDataSource: HikariDataSource
    val hikariConfig: HikariConfig

    init {
        hikariConfig = HikariConfig().apply {
            jdbcUrl = daoConfig.jdbcUrl
            maximumPoolSize = daoConfig.poolSize
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        hikariDataSource = if (daoConfig.remote)
            createDataSource(hikariConfig, Role.ADMIN)
        else
            HikariDataSource(hikariConfig.apply {
                username = daoConfig.username
                password = daoConfig.password
            })
        runFlywayMigrations(hikariDataSource)
        if (daoConfig.remote)
            changeRoleToUser()
    }

    override val connection: Connection
        get() = hikariDataSource.connection


    private fun createDataSource(hikariConfig: HikariConfig, role: Role) : HikariDataSource {
        return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig,
            daoConfig.dbCredMountPath,
            "${daoConfig.databaseName}-$role"
        )
    }

    private fun changeRoleToUser() {
        hikariDataSource.close()
        hikariDataSource = createDataSource(hikariConfig, Role.USER)
    }

    private fun runFlywayMigrations(hikariDataSource: HikariDataSource) = Flyway.configure().run {
        dataSource(hikariDataSource)
        initSql("SET ROLE \"${daoConfig.databaseName}-${Role.ADMIN}\"") //
        load().migrate().migrationsExecuted
    }
}
