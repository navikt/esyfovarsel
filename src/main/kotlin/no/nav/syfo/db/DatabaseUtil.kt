package no.nav.syfo.db

import no.nav.syfo.db.domain.PMikrofrontendSynlighet
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import java.sql.ResultSet

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

fun ResultSet.toPUtsendtVarsel() = PUtsendtVarsel(
    uuid = getString("uuid"),
    fnr = getString("fnr"),
    aktorId = getString("aktor_id"),
    narmesteLederFnr = getString("narmesteleder_fnr"),
    orgnummer = getString("orgnummer"),
    type = getString("type"),
    kanal = getString("kanal"),
    utsendtTidspunkt = getTimestamp("utsendt_tidspunkt").toLocalDateTime(),
    planlagtVarselId = getString("planlagt_varsel_id"),
    eksternReferanse = getString("ekstern_ref"),
    ferdigstiltTidspunkt = getTimestamp("ferdigstilt_tidspunkt")?.toLocalDateTime(),
    arbeidsgivernotifikasjonMerkelapp = getString("arbeidsgivernotifikasjon_merkelapp"),
    isForcedLetter = getBoolean("is_forced_letter"),
    journalpostId = getString("journalpost_id"),
)

fun ResultSet.toPMikrofrontendSynlighet() = PMikrofrontendSynlighet(
    uuid = getString("uuid"),
    synligFor = getString("synlig_for"),
    tjeneste = getString("tjeneste"),
    synligTom = getDate("synlig_tom")?.toLocalDate(),
    opprettet = getTimestamp("opprettet").toLocalDateTime(),
    sistEndret = getTimestamp("sist_endret").toLocalDateTime()
)

fun ResultSet.toPUtsendtVarselFeilet() = PUtsendtVarselFeilet(
    uuid = getString("uuid"),
    uuidEksternReferanse = getString("uuid_ekstern_referanse"),
    arbeidstakerFnr = getString("arbeidstaker_fnr"),
    narmesteLederFnr = getString("narmesteleder_fnr"),
    orgnummer = getString("orgnummer"),
    hendelsetypeNavn = getString("hendelsetype_navn"),
    arbeidsgivernotifikasjonMerkelapp = getString("arbeidsgivernotifikasjon_merkelapp"),
    brukernotifikasjonerMeldingType = getString("brukernotifikasjoner_melding_type"),
    journalpostId = getString("journalpost_id"),
    kanal = getString("kanal"),
    feilmelding = getString("feilmelding"),
    utsendtForsokTidspunkt = getTimestamp("utsendt_forsok_tidspunkt").toLocalDateTime(),
    isForcedLetter = getBoolean("is_forced_letter"),
    isResendt = getBoolean("is_resendt"),
)
