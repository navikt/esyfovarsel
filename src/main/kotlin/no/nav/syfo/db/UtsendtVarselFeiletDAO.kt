package no.nav.syfo.db

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import no.nav.syfo.db.domain.PUtsendtVarselFeilet

fun DatabaseInterface.storeUtsendtVarselFeilet(varsel: PUtsendtVarselFeilet) {
    val insertStatement = """INSERT INTO UTSENDT_VARSEL_FEILET (
        uuid,
        uuid_ekstern_referanse,
        arbeidstaker_fnr,   
        narmesteleder_fnr,
        orgnummer,
        hendelsetype_navn,
        arbeidsgivernotifikasjon_merkelapp,
        brukernotifikasjoner_melding_type,
        journalpost_id,
        kanal,
        feilmelding,
        utsendt_forsok_tidspunkt,
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent()

    connection.use { connection ->
        connection.prepareStatement(insertStatement).use {
            it.setObject(1, UUID.fromString(varsel.uuid))
            it.setString(2, varsel.uuidEksternReferanse)
            it.setString(3, varsel.arbeidstakerFnr)
            it.setString(4, varsel.narmesteLederFnr)
            it.setString(5, varsel.orgnummer)
            it.setString(6, varsel.hendelsetypeNavn)
            it.setString(7, varsel.arbeidsgivernotifikasjonMerkelapp)
            it.setString(8, varsel.brukernotifikasjonerMeldingType)
            it.setString(9, varsel.journalpostId)
            it.setString(10, varsel.kanal)
            it.setString(11, varsel.feilmelding)
            it.setTimestamp(12, Timestamp.valueOf(varsel.utsendtForsokTidspunkt))
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
    uuidEksternReferanse = getString("uuid_ekstern_referanse"),
    narmesteLederFnr = getString("narmesteleder_fnr"),
    arbeidstakerFnr = getString("fnr"),
    orgnummer = getString("orgnummer"),
    hendelsetypeNavn = getString("hendelsetype_navn"),
    arbeidsgivernotifikasjonMerkelapp = getString("arbeidsgivernotifikasjon_merkelapp"),
    brukernotifikasjonerMeldingType = getString("brukernotifikasjoner_melding_type"),
    journalpostId = getString("journalpost_id"),
    kanal = getString("kanal"),
    feilmelding = getString("feilmelding"),
    utsendtForsokTidspunkt = getTimestamp("utsendt_forsok_tidspunkt").toLocalDateTime(),
)

/*
*  uuid,
        uuid_ekstern_referanse,
        arbeidsgiver_fnr,
        arbeidstaker_fnr,
        orgnummer,
        hendelsetype_navn,
        arbeidsgivernotifikasjon_merkelapp,
        brukernotifikasjoner_melding_type,
        journalpost_id,
        kanal,
        feilmelding,
        utsendt_forsok_tidspunkt,
        * */