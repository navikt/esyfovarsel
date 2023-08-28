package no.nav.syfo.db

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.domain.PersonIdent

fun DatabaseInterface.storeUtsendtVarselFeilet(varsel: PUtsendtVarselFeilet) {
    val insertStatement = """INSERT INTO UTSENDING_VARSEL_FEILET (
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
        utsendt_forsok_tidspunkt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent()

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


fun DatabaseInterface.deleteUtsendtVarselFeiletByFnr(fnr: PersonIdent) {
    val updateStatement = """DELETE FROM UTSENDING_VARSEL_FEILET
                   WHERE arbeidstaker_fnr = ?
    """.trimMargin()

    return connection.use { connection ->
        connection.prepareStatement(updateStatement).use {
            it.setString(1, fnr.value)
            it.executeUpdate()
        }
        connection.commit()
    }
}


fun ResultSet.toPUtsendtVarselFeilet() = PUtsendtVarselFeilet(
    uuid = getString("uuid"),
    uuidEksternReferanse = getString("uuid_ekstern_referanse"),
    narmesteLederFnr = getString("narmesteleder_fnr"),
    arbeidstakerFnr = getString("arbeidstaker_fnr"),
    orgnummer = getString("orgnummer"),
    hendelsetypeNavn = getString("hendelsetype_navn"),
    arbeidsgivernotifikasjonMerkelapp = getString("arbeidsgivernotifikasjon_merkelapp"),
    brukernotifikasjonerMeldingType = getString("brukernotifikasjoner_melding_type"),
    journalpostId = getString("journalpost_id"),
    kanal = getString("kanal"),
    feilmelding = getString("feilmelding"),
    utsendtForsokTidspunkt = getTimestamp("utsendt_forsok_tidspunkt").toLocalDateTime(),
)
