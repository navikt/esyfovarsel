package no.nav.syfo.db

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

fun DatabaseInterface.storeFodselsdato(fnr: String, fodselsdato: String?) {
    val now = LocalDateTime.now()
    val insertStatement = """INSERT INTO FODSELSDATO  (
        uuid,
        fnr,
        fodselsdato,
        opprettet_tidspunkt) VALUES (?,?,?,?)
    """.trimIndent()
    connection.use { connection ->
        connection.prepareStatement(insertStatement).use {
            it.setObject(1, UUID.randomUUID())
            it.setString(2, fnr)
            it.setString(3, fodselsdato)
            it.setTimestamp(4, Timestamp.valueOf(now))
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.fetchFodselsdatoByFnr(fnr: String): List<String?> {
    val queryStatement = """SELECT *
                            FROM FODSELSDATO
                            WHERE fnr = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { getString("fodselsdato") }
        }
    }
}
