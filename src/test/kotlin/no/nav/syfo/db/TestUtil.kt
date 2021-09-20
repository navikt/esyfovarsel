package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import java.sql.Timestamp
import java.util.*

fun DatabaseInterface.storeUtsendtVarselTest(planlagtVarsel: PPlanlagtVarsel) {
    val insertStatement1 = """INSERT INTO UTSENDT_VARSEL (
        uuid,
        fnr,
        aktor_id,
        type,
        utsendt_tidspunkt,
        planlagt_varsel_id) VALUES (?, ?, ?, ?, ?, ?)""".trimIndent()

    val utsendtDato = Timestamp.valueOf(planlagtVarsel.utsendingsdato.atStartOfDay())
    val varselUUID = UUID.randomUUID()

    connection.use { connection ->
        connection.prepareStatement(insertStatement1).use {
            it.setObject(1, varselUUID)
            it.setString(2, planlagtVarsel.fnr)
            it.setString(3, planlagtVarsel.aktorId)
            it.setString(4, planlagtVarsel.type)
            it.setTimestamp(5, utsendtDato)
            it.setObject(6, UUID.fromString(planlagtVarsel.uuid))
            it.executeUpdate()
        }

        connection.commit()
    }
}
