package no.nav.syfo.db

import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun DatabaseInterface.storeInfotrygdUtbetaling(fnr: String, sykepengerMaxDate: LocalDate, utbetaltTilDate: LocalDate, gjenstaendeSykepengedager: Int) {
    val now = LocalDateTime.now()
    val insertStatement = """INSERT INTO UTBETALING_INFOTRYGD  (
        uuid, 
        fnr, 
        max_date, 
        utbet_tom,
        gjenstaende_sykepengedager,
        opprettet) VALUES (?,?,?,?,?,?)
    """.trimIndent()
    connection.use { connection ->
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
    }
}
