package no.nav.syfo.db

import no.nav.syfo.db.domain.PSyketilfellebit
import no.nav.syfo.syketilfelle.domain.Syketilfellebit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLException

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.db.SyketilfelleDAO")
fun DatabaseInterface.storeSyketilfellebit(pSyketilfellebit: PSyketilfellebit) {
    val insertStatement = """INSERT INTO SYKETILFELLEBIT (
        uuid,
        id,
        fnr,
        orgnummer,
        opprettet,
        opprettet_opprinnelig,
        inntruffet,
        tags,
        ressurs_id,
        fom,
        tom,
        korrigert_soknad) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
    """.trimIndent()
    try {
        connection.use { conn ->
            conn.prepareStatement(insertStatement).use {
                it.setObject(1, pSyketilfellebit.uuid)
                it.setString(2, pSyketilfellebit.id)
                it.setString(3, pSyketilfellebit.fnr)
                it.setString(4, pSyketilfellebit.orgnummer)
                it.setTimestamp(5, pSyketilfellebit.opprettet)
                it.setTimestamp(6, pSyketilfellebit.opprettetOpprinnelig)
                it.setTimestamp(7, pSyketilfellebit.inntruffet)
                it.setString(8, pSyketilfellebit.tags)
                it.setString(9, pSyketilfellebit.ressursId)
                it.setDate(10, pSyketilfellebit.fom)
                it.setDate(11, pSyketilfellebit.tom)
                it.setString(12, pSyketilfellebit.korrigertSoknad)
                it.executeUpdate()
            }
            conn.commit()
        }
    } catch (e: SQLException) {
        if (e.sqlState == errorCodeUniqueViolation) {
            log.warn("Received duplicate syketilfellebit with id ${pSyketilfellebit.id}. Skipping store.")
            return
        }
        throw e
    }
}

fun DatabaseInterface.fetchSyketilfellebiterByFnr(fnr: String): List<Syketilfellebit> {
    val queryStatement = """SELECT *
                            FROM SYKETILFELLEBIT
                            WHERE fnr = ?
    """.trimIndent()

    return connection.use { conn ->
        conn.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toSyketilfellebit() }
        }
    }
}

fun DatabaseInterface.deleteSyketilfellebitById(id: String) {
    val queryStatement1 = """DELETE
                            FROM SYKETILFELLEBIT
                            WHERE id = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(queryStatement1).use {
            it.setString(1, id)
            it.executeUpdate()
        }

        connection.commit()
    }
}
