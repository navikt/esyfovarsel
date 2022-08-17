package no.nav.syfo.service

import no.nav.syfo.DINE_SYKMELDTE_OPPFOLGINGSPLAN_OPPRETTET_TEKST
import no.nav.syfo.DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import java.time.OffsetDateTime

class OppfolgingsplanVarselService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer
) {
    fun sendVarselTilNarmesteLeder(varselHendelse: NarmesteLederHendelse) {
        val varseltekst = when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST
            NL_OPPFOLGINGSPLAN_OPPRETTET -> DINE_SYKMELDTE_OPPFOLGINGSPLAN_OPPRETTET_TEKST
            else -> {
                throw IllegalArgumentException("Type må være Oppfølgingsplan-type")
            }
        }
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            varselHendelse.arbeidstakerFnr,
            varselHendelse.orgnummer,
            varselHendelse.type.toDineSykmeldteHendelseType().toString(),
            null,
            varseltekst,
            OffsetDateTime.now().plusWeeks(4L)
        )
        dineSykmeldteHendelseKafkaProducer.sendVarsel(dineSykmeldteVarsel)
    }
}
