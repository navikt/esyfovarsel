package no.nav.syfo.db.domain

import no.nav.syfo.kafka.producers.minesidemicrofrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.minesidemicrofrontend.Tjeneste
import java.time.LocalDate
import java.time.LocalDateTime

data class PMikrofrontendSynlighet(
    val uuid: String,
    val synligFor: String,
    val tjeneste: String,
    val synligTom: LocalDate?,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime,
)

fun PMikrofrontendSynlighet.toMikrofrontendSynlighet() =
    MikrofrontendSynlighet(
        synligFor = this.synligFor,
        tjeneste = Tjeneste.valueOf(this.tjeneste),
        synligTom = this.synligTom,
    )
