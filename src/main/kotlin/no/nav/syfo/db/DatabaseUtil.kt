package no.nav.syfo.db

import java.sql.ResultSet
import no.nav.syfo.db.domain.PMikrofrontendSynlighet
import no.nav.syfo.db.domain.PUtsendtVarsel

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
    ferdigstiltTidspunkt = getTimestamp("ferdigstilt_tidspunkt")?.toLocalDateTime(),
    planlagtVarselId = getString("planlagt_varsel_id"),
    eksternReferanse = getString("ekstern_ref"),
    arbeidsgivernotifikasjonMerkelapp = getString("arbeidsgivernotifikasjon_merkelapp")
)

fun ResultSet.toPMikrofrontendSynlighet() = PMikrofrontendSynlighet(
    uuid = getString("uuid"),
    synligFor = getString("synlig_for"),
    tjeneste = getString("tjeneste"),
    synligTom = getDate("synlig_tom")?.toLocalDate(),
    opprettet = getTimestamp("opprettet").toLocalDateTime(),
    sistEndret = getTimestamp("sist_endret").toLocalDateTime()
)
