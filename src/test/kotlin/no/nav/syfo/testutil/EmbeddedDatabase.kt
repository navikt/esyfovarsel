package no.nav.syfo.testutil

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.db.DatabaseInterface
import org.flywaydb.core.Flyway
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.time.Duration

class EmbeddedDatabase : DatabaseInterface {
    companion object {
        // Shared container for all test classes
        private val postgresContainer =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:17")).apply {
                withDatabaseName("test")
                withUsername("test")
                withPassword("test")
                withReuse(true)
                withLabel("reuse.UUID", "esyfovarsel-test-db")
                withStartupTimeout(Duration.ofSeconds(10))
                withEnv("POSTGRES_HOST_AUTH_METHOD", "trust")
            }

        init {
            postgresContainer.start()
        }
    }

    private val dataSource: HikariDataSource by lazy {
        val config =
            HikariConfig().apply {
                jdbcUrl = postgresContainer.jdbcUrl
                username = postgresContainer.username
                password = postgresContainer.password
                maximumPoolSize = 4
                minimumIdle = 1
                connectionTimeout = 10000
                initializationFailTimeout = 30000
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            }

        HikariDataSource(config).also {
            // Flyway runs on its own driver-based connection with autoCommit=true (Flyway's default)
            // so that migrations using CREATE INDEX CONCURRENTLY (which cannot run inside a
            // transaction block) work correctly. The application pool uses autoCommit=false.
            //
            // transactionalLock=false makes Flyway take a session-level advisory lock instead of a
            // transactional one. A transactional lock keeps a connection idle-in-transaction for the
            // whole migration run, and that open snapshot blocks CREATE INDEX CONCURRENTLY indefinitely.
            Flyway
                .configure()
                .dataSource(postgresContainer.jdbcUrl, postgresContainer.username, postgresContainer.password)
                .cleanDisabled(false)
                .apply {
                    getConfigurationExtension(PostgreSQLConfigurationExtension::class.java).isTransactionalLock = false
                }.load()
                .apply {
                    clean()
                    migrate()
                }
        }
    }

    fun dropData() {
        val tables =
            listOf(
                "SYKMELDING_IDS",
                "UTSENDT_VARSEL",
                "MIKROFRONTEND_SYNLIGHET",
                "UTSENDING_VARSEL_FEILET",
                "ARBEIDSGIVERNOTIFIKASJONER_SAK",
                "ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE",
            )

        connection.use { connection ->
            tables.forEach { table ->
                connection.prepareStatement("DELETE FROM $table").executeUpdate()
            }
            connection.commit()
        }
    }

    override val connection: Connection
        get() = dataSource.connection.apply { autoCommit = false }
    override val log: Logger
        get() = LoggerFactory.getLogger(EmbeddedDatabase::class.qualifiedName)
}
