package no.nav.syfo.testutil

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.db.DatabaseInterface
import org.flywaydb.core.Flyway
import java.sql.Connection

class EmbeddedDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = EmbeddedPostgres.start()

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }

}

fun Connection.dropData() {
    val query1 = "DELETE FROM PLANLAGT_VARSEL"
    val query2 = "DELETE FROM SYKMELDING_IDS"
    val query3 = "DELETE FROM UTSENDT_VARSEL"

    use { connection ->
        connection.prepareStatement(query1).executeUpdate()
        connection.prepareStatement(query2).executeUpdate()
        connection.prepareStatement(query3).executeUpdate()
        connection.commit()
    }
}
