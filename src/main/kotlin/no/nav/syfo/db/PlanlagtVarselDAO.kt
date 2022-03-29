package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
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
                it.setObject(3, varselUUID)

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

fun DatabaseInterface.fetchPlanlagtVarselByUtsendingsdato(utsendingsdato: LocalDate): List<PPlanlagtVarsel> {
    val queryStatement = """SELECT *
                            FROM PLANLAGT_VARSEL
                            WHERE utsendingsdato = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setTimestamp(1, Timestamp.valueOf(utsendingsdato.atStartOfDay()))
            it.executeQuery().toList { toPPlanlagtVarsel() }
        }
    }
}

fun DatabaseInterface.fetchPlanlagtVarselByTypeAndUtsendingsdato(type: VarselType, fromDate: LocalDate, toDate: LocalDate): List<PPlanlagtVarsel> {
    val queryStatement = """SELECT *
                            FROM PLANLAGT_VARSEL
                            WHERE type = ?
                            AND utsendingsdato >= ? 
                            AND utsendingsdato <= ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, type.name)
            it.setDate(2, Date.valueOf(fromDate))
            it.setDate(3, Date.valueOf(toDate))
            it.executeQuery().toList { toPPlanlagtVarsel() }
        }
    }
}

fun DatabaseInterface.fetchSykmeldingerIdByPlanlagtVarselsUUID(uuid: String): List<String> {
    val queryStatement = """SELECT *
                            FROM SYKMELDING_IDS
                            WHERE varsling_id = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setObject(1, UUID.fromString(uuid))
            it.executeQuery().toVarslingIdsListe()
        }
    }
}

fun DatabaseInterface.fetchAllSykmeldingIdsAndCount(): Int {
    val queryStatement = """SELECT *
                            FROM SYKMELDING_IDS
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE).use {
            it.executeQuery().toVarslingIdsListeCount()
        }
    }
}

fun DatabaseInterface.updateUtsendingsdatoByVarselId(uuid: String, utsendingsdato: LocalDate) {
    val now = LocalDateTime.now()
    val updateStatement = """UPDATE PLANLAGT_VARSEL
                             SET UTSENDINGSDATO = ?,
                                 SIST_ENDRET = ?
                             WHERE UUID = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setDate(1, Date.valueOf(utsendingsdato))
            it.setTimestamp(2, Timestamp.valueOf(now))
            it.setObject(3, UUID.fromString(uuid))
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.deletePlanlagtVarselByVarselId(uuid: String) {
    val queryStatement1 = """DELETE
                            FROM PLANLAGT_VARSEL
                            WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(queryStatement1).use {
            it.setObject(1, UUID.fromString(uuid))
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.deletePlanlagtVarselBySykmeldingerId(sykmeldingerId: Set<String>) {
    val st1 = """DELETE
        FROM PLANLAGT_VARSEL
        WHERE uuid IN (SELECT varsling_id FROM SYKMELDING_IDS WHERE sykmelding_id = ? )
    """.trimMargin()

    connection.use { connection ->
        connection.prepareStatement(st1).use {
            for (sykmeldingId: String in sykmeldingerId) {
                it.setString(1, sykmeldingId)
                it.addBatch()
            }
            it.executeBatch()
        }

        connection.commit()
    }
}

fun DatabaseInterface.grantAccessToIAMUsers() {
    val statement = """
        GRANT ALL ON ALL TABLES IN SCHEMA PUBLIC TO CLOUDSQLIAMUSER
    """.trimIndent()

    connection.use { conn ->
        conn.prepareStatement(statement).use {
            it.executeUpdate()
        }
        conn.commit()
    }
}
