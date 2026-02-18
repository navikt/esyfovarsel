package no.nav.syfo.db.domain

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste

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
