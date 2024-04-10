package no.nav.syfo.db

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingSpleis
import java.sql.Date
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

fun DatabaseInterface.storeSpleisUtbetaling(utbetaling: UtbetalingSpleis) {
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
        } catch (e: SQLIntegrityConstraintViolationException) {
            log.debug("Ignoring duplicate message from Spleis with utbetalingID " + utbetaling.utbetalingId)
        }
    }
}

fun DatabaseInterface.deleteUtbetalingSpleisByFnr(fnr: PersonIdent) {
    val updateStatement = """DELETE FROM UTBETALING_SPLEIS
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
