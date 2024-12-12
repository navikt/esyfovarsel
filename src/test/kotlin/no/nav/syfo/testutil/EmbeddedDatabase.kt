package no.nav.syfo.testutil

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.db.DatabaseInterface
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

class EmbeddedDatabase : DatabaseInterface {
    private val dataSource: HikariDataSource

    init {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:lps-db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
            username = "sa"
            password = ""
            maximumPoolSize = 2
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        dataSource = HikariDataSource(config)

        Flyway.configure().dataSource(dataSource).load().apply {
            migrate()
            validate()
        }
    }

    fun dropData() {
        val tables = listOf(
            "SYKMELDING_IDS",
            "UTSENDT_VARSEL",
            "MIKROFRONTEND_SYNLIGHET",
            "UTSENDING_VARSEL_FEILET",
            "ARBEIDSGIVERNOTIFIKASJONER_SAK",
            "ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE"
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
