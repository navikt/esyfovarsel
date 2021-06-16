package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.ResultSet

private val log: Logger = LoggerFactory.getLogger("package no.nav.syfo.db.DatabaseUtil")

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

fun ResultSet.toVarslingIdsListe(): List<String> {
    val rows = ArrayList<String>()
    while (this.next()) {
        rows.add(getString("sykmelding_id"))
    }
    return rows
}

fun ResultSet.toVarslingIdsListeCount(): Int {
    try {
        this.last()
        return this.row
    } catch (e: Exception) {
        log.debug("Could process reult setØ ${e.message}")
    } finally {
        try {
            this.beforeFirst()
        } catch (e: Exception) {
            log.debug("Could process reult setØ ${e.message}")
        }
    }

    return 0
}