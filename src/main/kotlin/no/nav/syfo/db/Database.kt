package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.DbEnv
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import java.sql.Connection

const val postgresJdbcPrefix = "jdbc:postgresql"
const val errorCodeUniqueViolation = "23505"
interface DatabaseInterface {
    val connection: Connection
}

class Database(val env: DbEnv) : DatabaseInterface {

    val hikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = generateJdbcUrlFromEnv(env)
            username = env.dbUsername
            password = env.dbPassword
            maximumPoolSize = 2
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
    )

    init {
        runFlywayMigrations(hikariDataSource)
    }

    override val connection: Connection
        get() = hikariDataSource.connection

    private fun runFlywayMigrations(hikariDataSource: HikariDataSource) = Flyway.configure().run {
        dataSource(hikariDataSource)
        load().migrate().migrationsExecuted
    }
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

class LocalDatabase(dbEnv: DbEnv) : DatabaseFSS(
    DbConfig(
        jdbcUrl = generateJdbcUrlFromEnv(dbEnv),
        databaseName = dbEnv.dbName,
        password = "password",
        username = "esyfovarsel-admin",
        remote = false
    )
)

class RemoteDatabase(dbEnv: DbEnv) : DatabaseFSS(
    DbConfig(
        jdbcUrl = generateJdbcUrlFromEnv(dbEnv),
        databaseName = dbEnv.dbName,
        dbCredMountPath = dbEnv.dbCredMounthPath
    )
)

abstract class DatabaseFSS(val daoConfig: DbConfig) : DatabaseInterface {
    enum class Role {
        ADMIN, USER;

        override fun toString() = name.lowercase()
    }

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
            HikariDataSource(
                hikariConfig.apply {
                    username = daoConfig.username
                    password = daoConfig.password
                }
            )
        runFlywayMigrations(hikariDataSource)
        if (daoConfig.remote)
            changeRoleToUser()
    }

    override val connection: Connection
        get() = hikariDataSource.connection

    private fun createDataSource(hikariConfig: HikariConfig, role: Role): HikariDataSource {
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

fun generateJdbcUrlFromEnv(env: DbEnv): String {
    return "$postgresJdbcPrefix://${env.dbHost}:${env.dbPort}/${env.dbName}"
}
