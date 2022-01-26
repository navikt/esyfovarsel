package no.nav.syfo.db

import no.nav.syfo.db.domain.PSyketilfellebit
import no.nav.syfo.db.domain.Syketilfellebit


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
        korrigert_soknad VALUES (?,?,?,?,?,?,?,?,?,?,?)""".trimIndent()


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
        }

        conn.commit()
    }
}

fun DatabaseInterface.fetchSyketilfellebitByFnr(fnr: String): List<Syketilfellebit> {
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
