package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import java.sql.ResultSet

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

fun ResultSet.toPPlanlagtVarsel() : PPlanlagtVarsel =
    PPlanlagtVarsel(
        uuid = getString("uuid"),
        fnr = getString("fnr"),
        aktorId = getString("aktor_id"),
        type = getString("type"),
        utsendingsdato = getDate("utsendingsdato").toLocalDate(),
        opprettet = getTimestamp("opprettet").toLocalDateTime(),
        sistEndret = getTimestamp("sist_endret").toLocalDateTime()
    )