package no.nav.syfo.db

import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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

fun DatabaseInterface.updateMaxDateByFnr(sykepengerMaxDate: LocalDate, fnr: String, source: String) {
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

fun DatabaseInterface.fetchMaxDateByFnr(fnr: String): LocalDate? {
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

fun DatabaseInterface.deleteMaxDateByFnr(fnr: String) {
    val deleteStatement = """DELETE FROM SYKEPENGER_MAX_DATE  WHERE fnr = ?""".trimIndent()

    connection.use { connection ->
        connection.prepareStatement(deleteStatement).use {
            it.setString(1, fnr)
            it.executeUpdate()
        }
        connection.commit()
    }
}
