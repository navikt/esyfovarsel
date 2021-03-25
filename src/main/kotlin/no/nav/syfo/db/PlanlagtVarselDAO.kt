package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*


fun DatabaseInterface.storePlanlagtVarsel(planlagtVarsel: PlanlagtVarsel) {
    val insertStatement = """INSERT INTO PLANLAGT_VARSEL (
        uuid,
        fnr,
        aktor_id,
        type,
        utsendingsdato,
        opprettet,
        sist_endret) VALUES (?, ?, ?, ?, ?, ?, ?)""".trimIndent()

    val now = Timestamp.valueOf(LocalDateTime.now())

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use {
            it.setObject(1, UUID.randomUUID())
            it.setString(2, planlagtVarsel.fnr)
            it.setString(3, planlagtVarsel.aktorId)
            it.setString(4, planlagtVarsel.type.name)
            it.setDate(5, Date.valueOf(planlagtVarsel.utsendingsdato))
            it.setTimestamp(6, now)
            it.setTimestamp(7, now)
            it.execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.fetchPlanlagtVarselByFnr(fnr: String) : List<PPlanlagtVarsel> {
    val queryStatement = """SELECT *
                            FROM PLANLAGT_VARSEL
                            WHERE fnr = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPPlanlagtVarsel() }
        }
    }
}
