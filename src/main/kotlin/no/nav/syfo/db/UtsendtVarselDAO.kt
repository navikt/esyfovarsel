package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

fun DatabaseInterface.storeUtsendtVarsel(planlagtVarsel: PPlanlagtVarsel) {
    val insertStatement1 = """INSERT INTO UTSENDT_VARSEL (
        uuid,
        fnr,
        aktor_id,
        type,
        utsendt_tidspunkt) VALUES (?, ?, ?, ?, ?)""".trimIndent()

    val now = Timestamp.valueOf(LocalDateTime.now())
    val varselUUID = UUID.randomUUID()

    connection.use { connection ->
        connection.prepareStatement(insertStatement1).use {
            it.setObject(1, varselUUID)
            it.setString(2, planlagtVarsel.fnr)
            it.setString(3, planlagtVarsel.aktorId)
            it.setString(4, planlagtVarsel.type)
            it.setTimestamp(5, now)
            it.executeUpdate()
        }

        connection.commit()
    }
}

