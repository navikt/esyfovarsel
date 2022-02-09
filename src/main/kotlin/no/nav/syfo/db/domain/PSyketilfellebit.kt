package no.nav.syfo.db.domain

import java.sql.Timestamp
import java.sql.Date
import java.util.*

data class PSyketilfellebit (
    val uuid: UUID,
    val id: String,
    val fnr: String,
    val orgnummer: String?,
    val opprettet: Timestamp,
    val opprettetOpprinnelig: Timestamp,
    val inntruffet: Timestamp,
    val tags: String,
    val ressursId: String,
    val fom: Date,
    val tom: Date,
    val korrigertSoknad: String?
)
