package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PUtsendtVarsel
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

fun DatabaseInterface.storeUtsendtVarsel(planlagtVarsel: PPlanlagtVarsel) {
    val insertStatement1 = """INSERT INTO UTSENDT_VARSEL (
        uuid,
        fnr,
        aktor_id,
        type,
        utsendt_tidspunkt,
        planlagt_varsel_id) VALUES (?, ?, ?, ?, ?, ?)""".trimIndent()

    val now = Timestamp.valueOf(LocalDateTime.now())
    val varselUUID = UUID.randomUUID()

    connection.use { connection ->
        connection.prepareStatement(insertStatement1).use {
            it.setObject(1, varselUUID)
            it.setString(2, planlagtVarsel.fnr)
            it.setString(3, planlagtVarsel.aktorId)
            it.setString(4, planlagtVarsel.type)
            it.setTimestamp(5, now)
            it.setObject(6, UUID.fromString(planlagtVarsel.uuid))
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.storeUtsendtVarsel(PUtsendtVarsel: PUtsendtVarsel) {
    val insertStatement1 = """INSERT INTO UTSENDT_VARSEL (
        uuid,
        narmesteLeder_fnr,
        fnr,   
        orgnummer,
        type,
        kanal,
        utsendt_tidspunkt,
        ekstern_ref) VALUES (?, ?, ?, ?, ?, ?, ?,?)""".trimIndent()

    connection.use { connection ->
        connection.prepareStatement(insertStatement1).use {
            it.setObject(1, UUID.fromString(PUtsendtVarsel.uuid))
            it.setString(2, PUtsendtVarsel.narmesteLederFnr)
            it.setString(3, PUtsendtVarsel.fnr)
            it.setString(4, PUtsendtVarsel.orgnummer)
            it.setString(5, PUtsendtVarsel.type)
            it.setString(6, PUtsendtVarsel.kanal)
            it.setTimestamp(7, Timestamp.valueOf(PUtsendtVarsel.utsendtTidspunkt))
            it.setString(8, PUtsendtVarsel.eksternReferanse)
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.fetchUtsendtVarselByFnr(fnr: String): List<PUtsendtVarsel> {
    val queryStatement = """SELECT *
                            FROM UTSENDT_VARSEL
                            WHERE fnr = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPUtsendtVarsel() }
        }
    }
}

