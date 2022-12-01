package no.nav.syfo.db

import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.kafka.consumers.utbetaling.domain.UtbetalingUtbetalt
import no.nav.syfo.utils.REMAINING_DAYS_UNTIL_39_UKERS_VARSEL
import org.postgresql.util.PSQLException

fun DatabaseInterface.storeSykepengerMaxDate(sykepengerMaxDate: LocalDate, fnr: String, source: String) {
    val now = LocalDateTime.now()
    val insertStatement = """INSERT INTO SYKEPENGER_MAX_DATE  (
        uuid, 
        fnr, 
        max_date, 
        opprettet, 
        sist_endret,
        source) VALUES (?,?,?,?,?,?)
    """.trimIndent()
    connection.use { connection ->
        connection.prepareStatement(insertStatement).use {
            it.setObject(1, UUID.randomUUID())
            it.setString(2, fnr)
            it.setDate(3, Date.valueOf(sykepengerMaxDate))
            it.setTimestamp(4, Timestamp.valueOf(now))
            it.setTimestamp(5, Timestamp.valueOf(now))
            it.setString(6, source)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.updateSykepengerMaxDateByFnr(sykepengerMaxDate: LocalDate, fnr: String, source: String) {
    val updateStatement = """UPDATE SYKEPENGER_MAX_DATE 
                             SET SOURCE = ?,
                                 MAX_DATE = ?,
                                 SIST_ENDRET = ?
                             WHERE FNR = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setString(1, source)
            it.setDate(2, Date.valueOf(sykepengerMaxDate))
            it.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()))
            it.setString(4, fnr)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.fetchSykepengerMaxDateByFnr(fnr: String): LocalDate? {
    val fetchStatement = """SELECT *  FROM SYKEPENGER_MAX_DATE  WHERE FNR = ?""".trimIndent()

    val storedMaxDateAsList = connection.use { connection ->
        connection.prepareStatement(fetchStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { getDate("max_date") }
        }
    }

    return if (storedMaxDateAsList.isNotEmpty()) {
        storedMaxDateAsList.first().toLocalDate()
    } else null
}

fun DatabaseInterface.fetchPlanlagtMerVeiledningVarselByUtsendingsdato(sendingDate: LocalDate): List<PPlanlagtVarsel> {
    val maxDate = sendingDate.plusDays(REMAINING_DAYS_UNTIL_39_UKERS_VARSEL)
    val queryStatement = """SELECT *
                            FROM SYKEPENGER_MAX_DATE
                            WHERE MAX_DATE = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setDate(1, Date.valueOf(maxDate))
            it.executeQuery().toList { toPPlanlagtVarselMerVeiledning(sendingDate) }
        }
    }
}

fun DatabaseInterface.deleteSykepengerMaxDateByFnr(fnr: String) {
    val deleteStatement = """DELETE FROM SYKEPENGER_MAX_DATE  WHERE fnr = ?""".trimIndent()

    connection.use { connection ->
        connection.prepareStatement(deleteStatement).use {
            it.setString(1, fnr)
            it.executeUpdate()
        }
        connection.commit()
    }
}

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
