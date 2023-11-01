package no.nav.syfo.db

import no.nav.syfo.db.domain.*
import java.sql.ResultSet
import java.util.*


fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

fun ResultSet.toPPlanlagtVarsel() = PPlanlagtVarsel(
    uuid = getString("uuid"),
    fnr = getString("fnr"),
    aktorId = getString("aktor_id"),
    orgnummer = getString("orgnummer"),
    type = getString("type"),
    utsendingsdato = getDate("utsendingsdato").toLocalDate(),
    opprettet = getTimestamp("opprettet").toLocalDateTime(),
    sistEndret = getTimestamp("sist_endret").toLocalDateTime()
)

fun ResultSet.toPUtbetaling() = PUtbetaling(
    id = UUID.fromString(getString("id")),
    fnr = getString("fnr"),
    utbetaltTom = getDate("utbetalt_tom").toLocalDate(),
    forelopigBeregnetSlutt = getDate("forelopig_beregnet_slutt").toLocalDate(),
    gjenstaendeSykedager = getInt("gjenstaende_sykedager"),
    opprettet = getTimestamp("opprettet").toLocalDateTime(),
)

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

fun ResultSet.toVarslingIdsListe(): List<String> {
    val rader = ArrayList<String>()
    while (this.next()) {
        rader.add(getString("sykmelding_id"))
    }
    return rader
}

fun ResultSet.toVarslingIdsListeCount(): Int {
    this.last()
    return this.row
}

fun ResultSet.toPMaksDato() = PMaksDato(
    id = getString("id"),
    fnr = getString("fnr"),
    forelopig_beregnet_slutt = getTimestamp("forelopig_beregnet_slutt").toLocalDateTime().toLocalDate(),
    utbetalt_tom = getTimestamp("utbetalt_tom").toLocalDateTime().toLocalDate(),
    gjenstaende_sykedager = getString("gjenstaende_sykedager"),
    opprettet = getTimestamp("opprettet").toLocalDateTime(),
)

fun ResultSet.toPMikrofrontendSynlighet() = PMikrofrontendSynlighet(
    uuid = getString("uuid"),
    synligFor = getString("synlig_for"),
    tjeneste = getString("tjeneste"),
    synligTom = getDate("synlig_tom")?.toLocalDate(),
    opprettet = getTimestamp("opprettet").toLocalDateTime(),
    sistEndret = getTimestamp("sist_endret").toLocalDateTime()
)
