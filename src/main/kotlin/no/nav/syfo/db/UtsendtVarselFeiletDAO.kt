package no.nav.syfo.db

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import no.nav.syfo.db.domain.PUtsendtVarselFeilet

fun DatabaseInterface.storeUtsendtVarselFeilet(varsel: PUtsendtVarselFeilet) {
    val insertStatement = """INSERT INTO UTSENDT_VARSEL_FEILET (
        uuid,
        narmesteLeder_fnr,
        fnr,   
        orgnummer,
        type,
        kanal,
        feilmelding,
        journalpost_id,
        utsendt_forsok_tidspunkt,
        ekstern_referanse
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent()

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use {
            it.setObject(1, UUID.fromString(varsel.uuid))
            it.setString(2, varsel.narmesteLederFnr)
            it.setString(3, varsel.fnr)
            it.setString(4, varsel.orgnummer)
            it.setString(5, varsel.type)
            it.setString(6, varsel.kanal)
            it.setString(7, varsel.feilmelding)
            it.setString(8, varsel.journalpostId)
            it.setTimestamp(9, Timestamp.valueOf(varsel.utsendtForsokTidspunkt))
            it.setString(10, varsel.eksternReferanse)
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.fetchUtsendtVarselFeiletByFnr(fnr: String): List<PUtsendtVarselFeilet> {
    val queryStatement = """SELECT *
                            FROM UTSENDT_VARSEL_FEILET
                            WHERE fnr = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(queryStatement).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPUtsendtVarselFeilet() }
        }
    }
}

fun ResultSet.toPUtsendtVarselFeilet() = PUtsendtVarselFeilet(
    uuid = getString("uuid"),
    fnr = getString("fnr"),
    narmesteLederFnr = getString("narmesteleder_fnr"),
    orgnummer = getString("orgnummer"),
    type = getString("type"),
    kanal = getString("kanal"),
    utsendtForsokTidspunkt = getTimestamp("utsendt_forsok_tidspunkt").toLocalDateTime(),
    eksternReferanse = getString("ekstern_referanse"),
    feilmelding = getString("feilmelding"),
    journalpostId = getString("journalpost_id"),
)