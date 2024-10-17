package no.nav.syfo.db

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.infotrygd.domain.InfotrygdSource
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime.now
import java.util.*

fun DatabaseInterface.storeInfotrygdUtbetaling(
    fnr: String,
    sykepengerMaxDate: LocalDate,
    utbetaltTilDate: LocalDate,
    gjenstaendeSykepengedager: Int,
    source: InfotrygdSource
) {
    val insertStatement = """INSERT INTO UTBETALING_INFOTRYGD  (
        ID, 
        FNR, 
        MAX_DATE, 
        UTBET_TOM,
        GJENSTAENDE_SYKEDAGER,
        OPPRETTET,
        SOURCE) VALUES (?,?,?,?,?,?,?)
    """.trimIndent()
    connection.use { connection ->
        try {
            connection.prepareStatement(insertStatement).use {
                it.setObject(1, UUID.randomUUID())
                it.setString(2, fnr)
                it.setDate(3, Date.valueOf(sykepengerMaxDate))
                it.setDate(4, Date.valueOf(utbetaltTilDate))
                it.setInt(5, gjenstaendeSykepengedager)
                it.setTimestamp(6, Timestamp.valueOf(now()))
                it.setString(7, source.name)
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            log.info("[INFOTRYGD KAFKA] Ignoring inserting a message from Infotrygd with max date $sykepengerMaxDate,  utbet tom $utbetaltTilDate and gjenstaendeSykepengedager $gjenstaendeSykepengedager")
        }
    }
}

//TODO: Currently only  used for testing, delete if not needed later on
fun DatabaseInterface.fetchInfotrygdUtbetalingByFnr(fnr: String): MutableList<Int> {
    val fetchStatement = """SELECT *  FROM UTBETALING_SPLEIS  WHERE FNR = ?""".trimIndent()

    val gjenstaaendeDagerAsList = connection.use { connection ->
        connection.prepareStatement(fetchStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { getInt("GJENSTAENDE_SYKEDAGER") }
        }
    }

    return gjenstaaendeDagerAsList
}


fun DatabaseInterface.deleteUtbetalingInfotrygdByFnr(fnr: PersonIdent) {
    val updateStatement = """DELETE FROM UTBETALING_INFOTRYGD
                   WHERE fnr = ?
    """.trimMargin()

    return connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setString(1, fnr.value)
            it.executeUpdate()
        }
        connection.commit()
    }
}
