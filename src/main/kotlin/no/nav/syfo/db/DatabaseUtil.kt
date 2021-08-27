package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.UtsendtVarsel
import java.sql.ResultSet

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

fun ResultSet.toPPlanlagtVarsel(): PPlanlagtVarsel =
    PPlanlagtVarsel(
        uuid = getString("uuid"),
        fnr = getString("fnr"),
        aktorId = getString("aktor_id"),
        type = getString("type"),
        utsendingsdato = getDate("utsendingsdato").toLocalDate(),
        opprettet = getTimestamp("opprettet").toLocalDateTime(),
        sistEndret = getTimestamp("sist_endret").toLocalDateTime()
    )

fun ResultSet.toUtsendtVarsel(): UtsendtVarsel =
    UtsendtVarsel(
        fnr = getString("fnr"),
        aktorId = getString("aktor_id"),
        type = getString("type"),
        utsendtTidspunkt = getTimestamp("utsendt_tidspunkt").toLocalDateTime(),
        planlagtVarselId = getString("planlagt_varsel_id")
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
