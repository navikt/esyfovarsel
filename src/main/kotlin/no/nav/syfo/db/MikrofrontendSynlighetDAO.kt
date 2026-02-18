package no.nav.syfo.db

import no.nav.syfo.db.domain.PMikrofrontendSynlighet
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

fun DatabaseInterface.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet: MikrofrontendSynlighet) {
    val insertStatement =
        """
        INSERT INTO MIKROFRONTEND_SYNLIGHET (
        uuid,
        synlig_for,
        tjeneste,
        synlig_tom,
        sist_endret,
        opprettet) VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

    val now = Timestamp.valueOf(LocalDateTime.now())
    val varselUUID = UUID.randomUUID()

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use {
            it.setObject(1, varselUUID)
            it.setString(2, mikrofrontendSynlighet.synligFor)
            it.setString(3, mikrofrontendSynlighet.tjeneste.name)
            it.setDate(4, mikrofrontendSynlighet.synligTom?.let { Date.valueOf(it) })
            it.setTimestamp(5, now)
            it.setTimestamp(6, now)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.updateMikrofrontendEntrySynligTomByExistingEntry(
    entry: MikrofrontendSynlighet,
    newSynligTom: LocalDate,
) = updateMikrofrontendEntrySynligTomByFnrAndTjeneste(entry.synligFor, entry.tjeneste, newSynligTom)

fun DatabaseInterface.updateMikrofrontendEntrySynligTomByFnrAndTjeneste(
    fnr: String,
    tjeneste: Tjeneste,
    newSynligTom: LocalDate,
) {
    val now = LocalDateTime.now()
    val updateStatement =
        """UPDATE MIKROFRONTEND_SYNLIGHET
                             SET synlig_tom = ?,
                                 sist_endret = ?
                             WHERE synlig_for = ? AND tjeneste = ?
        """.trimMargin()

    connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setDate(1, Date.valueOf(newSynligTom))
            it.setTimestamp(2, Timestamp.valueOf(now))
            it.setString(3, fnr)
            it.setString(4, tjeneste.name)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.deleteMikrofrontendSynlighetEntryByFnrAndTjeneste(
    fnr: String,
    tjeneste: Tjeneste,
) {
    val updateStatement =
        """
        DELETE
        FROM MIKROFRONTEND_SYNLIGHET
        WHERE synlig_for = ? AND tjeneste = ?
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setString(1, fnr)
            it.setString(2, tjeneste.name)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.fetchMikrofrontendSynlighetEntriesByFnr(fnr: String): List<PMikrofrontendSynlighet> {
    val queryStatement =
        """
        SELECT *
        FROM MIKROFRONTEND_SYNLIGHET
        WHERE synlig_for = ?
        """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPMikrofrontendSynlighet() }
        }
    }
}

fun DatabaseInterface.fetchFnrsWithExpiredMicrofrontendEntries(tjeneste: Tjeneste): List<String> {
    val today = LocalDate.now()
    val queryStatement =
        """
        SELECT synlig_for
        FROM MIKROFRONTEND_SYNLIGHET
        WHERE tjeneste = ? AND synlig_tom <= ?
        """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, tjeneste.name)
            it.setDate(2, Date.valueOf(today))
            it.executeQuery().toList { getString("synlig_for") }
        }
    }
}

fun DatabaseInterface.deleteMikrofrontendSynlighetByFnr(fnr: PersonIdent) {
    val updateStatement =
        """DELETE 
        FROM MIKROFRONTEND_SYNLIGHET
                   WHERE synlig_for = ?
        """.trimMargin()

    return connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setString(1, fnr.value)
            it.executeUpdate()
        }
        connection.commit()
    }
}
