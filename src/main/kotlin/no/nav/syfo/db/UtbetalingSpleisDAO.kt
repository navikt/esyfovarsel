package no.nav.syfo.db

import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import org.postgresql.util.PSQLException

fun DatabaseInterface.storeSpleisUtbetaling(utbetaling: UtbetalingUtbetalt) {
    val insertStatement = """INSERT INTO UTBETALING_SPLEIS  (
        ID, 
        FNR, 
        ORGANISASJONSNUMMER, 
        EVENT, 
        TYPE,
        FORELOPIG_BEREGNET_SLUTT,
        FORBRUKTE_SYKEDAGER,
        GJENSTAENDE_SYKEDAGER,
        STONADSDAGER,
        ANTALL_VEDTAK,
        FOM,
        TOM,
        UTBETALING_ID,
        KORRELASJON_ID,
        OPPRETTET) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    """.trimIndent()
    connection.use { connection ->
        try {
            connection.prepareStatement(insertStatement).use {
                it.setObject(1, UUID.randomUUID())
                it.setString(2, utbetaling.fødselsnummer)
                it.setString(3, utbetaling.organisasjonsnummer)
                it.setString(4, utbetaling.event)
                it.setString(5, utbetaling.type)
                it.setDate(6, Date.valueOf(utbetaling.foreløpigBeregnetSluttPåSykepenger))
                it.setInt(7, utbetaling.forbrukteSykedager!!) //Ikke nullable for event utbetaling_utbetalt
                it.setInt(8, utbetaling.gjenståendeSykedager!!) //Ikke nullable for event utbetaling_utbetalt
                it.setInt(9, utbetaling.stønadsdager!!) //Ikke nullable for event utbetaling_utbetalt
                it.setInt(10, utbetaling.antallVedtak!!) //Ikke nullable for event utbetaling_utbetalt
                it.setDate(11, Date.valueOf(utbetaling.fom))
                it.setDate(12, Date.valueOf(utbetaling.tom))
                it.setString(13, utbetaling.utbetalingId)
                it.setString(14, utbetaling.korrelasjonsId)
                it.setTimestamp(15, Timestamp.valueOf(LocalDateTime.now()))
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: PSQLException) {
            log.debug("Ignoring duplicate message from Spleis with utbetalingID " + utbetaling.utbetalingId)
        }
    }
}

//TODO: Currently only  used for testing, delete if not needed later on
fun DatabaseInterface.fetchSpleisUtbetalingByFnr(fnr: String): MutableList<Int> {
    val fetchStatement = """SELECT *  FROM UTBETALING_SPLEIS  WHERE FNR = ?""".trimIndent()

    val gjenstaaendeDagerAsList = connection.use { connection ->
        connection.prepareStatement(fetchStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { getInt("GJENSTAENDE_SYKEDAGER") }
        }
    }

    return gjenstaaendeDagerAsList
}
