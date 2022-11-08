package no.nav.syfo.db

import org.postgresql.util.PSQLException
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun DatabaseInterface.storeInfotrygdUtbetaling(fnr: String, sykepengerMaxDate: LocalDate, utbetaltTilDate: LocalDate, gjenstaendeSykepengedager: Int) {
    val now = LocalDateTime.now()
    val insertStatement = """INSERT INTO UTBETALING_INFOTRYGD  (
        ID, 
        FNR, 
        MAX_DATE, 
        UTBET_TOM,
        GJENSTAENDE_SYKEDAGER,
        OPPRETTET) VALUES (?,?,?,?,?,?)
    """.trimIndent()
    connection.use { connection ->
        try {
            connection.prepareStatement(insertStatement).use {
                it.setObject(1, UUID.randomUUID())
                it.setString(2, fnr)
                it.setDate(3, Date.valueOf(sykepengerMaxDate))
                it.setDate(4, Date.valueOf(utbetaltTilDate))
                it.setInt(5, gjenstaendeSykepengedager)
                it.setTimestamp(6, Timestamp.valueOf(now))
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: PSQLException) {
            log.debug("Ignoring duplicate message from Infotrygd with max date $sykepengerMaxDate and utbet tom $utbetaltTilDate")
        }
    }
}
