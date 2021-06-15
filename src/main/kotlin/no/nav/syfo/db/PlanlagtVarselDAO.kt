package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*


fun DatabaseInterface.storePlanlagtVarsel(planlagtVarsel: PlanlagtVarsel) {
    val insertStatement1 = """INSERT INTO PLANLAGT_VARSEL (
        uuid,
        fnr,
        aktor_id,
        type,
        utsendingsdato,
        opprettet,
        sist_endret) VALUES (?, ?, ?, ?, ?, ?, ?)""".trimIndent()

    val insertStatement2 = """INSERT INTO SYKMELDING_IDS (
        uuid,
        sykmelding_id,
        varsling_id) VALUES (?, ?, ?)""".trimIndent()

    val now = Timestamp.valueOf(LocalDateTime.now())
    val varselUUID = UUID.randomUUID()

    connection.use { connection ->
        connection.prepareStatement(insertStatement1).use {
            it.setObject(1, varselUUID)
            it.setString(2, planlagtVarsel.fnr)
            it.setString(3, planlagtVarsel.aktorId)
            it.setString(4, planlagtVarsel.type.name)
            it.setDate(5, Date.valueOf(planlagtVarsel.utsendingsdato))
            it.setTimestamp(6, now)
            it.setTimestamp(7, now)
            it.executeUpdate()
        }

        connection.prepareStatement(insertStatement2).use {
            for (sykmeldingId: String in planlagtVarsel.sykmeldingerId) {
                it.setObject(1, UUID.randomUUID())
                it.setString(2, sykmeldingId)
                it.setString(3, varselUUID.toString())

                it.addBatch()
            }
            it.executeBatch()
        }

        connection.commit()
    }
}

fun DatabaseInterface.fetchPlanlagtVarselByFnr(fnr: String): List<PPlanlagtVarsel> {
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

fun DatabaseInterface.fetchSykmeldingerIdByPlanlagtVarselsUUID(uuid: String): List<Map<String, List<String>>> {
    val queryStatement = """SELECT *
                            FROM SYKMELDING_IDS
                            WHERE varsling_id = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, uuid)
            it.executeQuery().toList { toSykmeldingerIdMap() }
        }
    }
}

fun DatabaseInterface.deletePlanlagtVarsel(uuid: String) {
    val queryStatement1 = """DELETE
                            FROM PLANLAGT_VARSEL
                            WHERE uuid = ?
    """.trimIndent()

    val queryStatement2 = """DELETE
                            FROM SYKMELDING_IDS
                            WHERE varsling_id = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(queryStatement1).use {
            it.setObject(1, UUID.fromString(uuid))
            it.executeUpdate()
        }

        connection.prepareStatement(queryStatement2).use {
            it.setObject(1, uuid)
            it.executeUpdate()
        }

        connection.commit()
    }
}
