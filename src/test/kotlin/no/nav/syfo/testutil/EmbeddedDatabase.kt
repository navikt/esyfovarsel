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

    override val connection: Connection
        get() = dataSource.connection.apply { autoCommit = false }
    override val log: Logger
        get() = LoggerFactory.getLogger(EmbeddedDatabase::class.qualifiedName)
}

fun Connection.dropData() {
    val query1 = "DELETE FROM PLANLAGT_VARSEL"
    val query2 = "DELETE FROM SYKMELDING_IDS"
    val query3 = "DELETE FROM UTSENDT_VARSEL"
    val query5 = "DELETE FROM UTBETALING_INFOTRYGD"
    val query6 = "DELETE FROM UTBETALING_SPLEIS"
    val query7 = "DELETE FROM MIKROFRONTEND_SYNLIGHET"

    use { connection ->
        connection.prepareStatement(query1).executeUpdate()
        connection.prepareStatement(query2).executeUpdate()
        connection.prepareStatement(query3).executeUpdate()
        connection.prepareStatement(query5).executeUpdate()
        connection.prepareStatement(query6).executeUpdate()
        connection.prepareStatement(query7).executeUpdate()
        connection.commit()
    }
}
