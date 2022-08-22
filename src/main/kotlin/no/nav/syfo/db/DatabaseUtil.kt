package no.nav.syfo.db

import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PSyketilfellebit
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.kafka.consumers.syketilfelle.domain.KSyketilfellebit
import no.nav.syfo.syketilfelle.domain.Syketilfellebit
import no.nav.syfo.syketilfelle.domain.tagsFromString
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
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

fun ResultSet.toPUtsendtVarsel() = PUtsendtVarsel(
    uuid = getString("uuid"),
    fnr = getString("fnr"),
    aktorId = getString("aktor_id"),
    narmesteLederFnr = getString("narmesteleder_fnr"),
    orgnummer = getString("orgnummer"),
    type = getString("type"),
    kanal = getString("kanal"),
    utsendtTidspunkt = getTimestamp("utsendt_tidspunkt").toLocalDateTime(),
    planlagtVarselId = getString("planlagt_varsel_id")
)

fun ResultSet.toSyketilfellebit() = Syketilfellebit(
    id = getString("id"),
    fnr = getString("fnr"),
    orgnummer = getString("orgnummer"),
    opprettet = getTimestamp("opprettet").toLocalDateTime(),
    inntruffet = getTimestamp("inntruffet").toLocalDateTime(),
    tags = getString("tags").tagsFromString(),
    ressursId = getString("ressurs_id"),
    fom = getDate("fom").toLocalDate(),
    tom = getDate("tom").toLocalDate()
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

fun KSyketilfellebit.toPSyketilfellebit(): PSyketilfellebit {
    return PSyketilfellebit(
        UUID.randomUUID(),
        this.id,
        this.fnr,
        this.orgnummer,
        Timestamp.valueOf(LocalDateTime.now()),
        Timestamp.valueOf(this.opprettet.toLocalDateTime()),
        Timestamp.valueOf(this.inntruffet.toLocalDateTime()),
        this.tags.reduce { acc, tag -> "$acc,$tag" },
        this.ressursId,
        Date.valueOf(this.fom),
        Date.valueOf(this.tom),
        this.korrigererSendtSoknad
    )
}
