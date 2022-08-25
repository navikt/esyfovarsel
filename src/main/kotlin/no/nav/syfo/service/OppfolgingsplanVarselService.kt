package no.nav.syfo.service

import no.nav.syfo.DINE_SYKMELDTE_OPPFOLGINGSPLAN_OPPRETTET_TEKST
import no.nav.syfo.DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_OPPFOLGINGSPLAN_OPPRETTET
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING
import no.nav.syfo.kafka.consumers.varselbus.domain.NarmesteLederHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import java.time.OffsetDateTime

class OppfolgingsplanVarselService(
    val senderFacade: SenderFacade
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
            ansattFnr = varselHendelse.arbeidstakerFnr,
            orgnr = varselHendelse.orgnummer,
            oppgavetype = varselHendelse.type.toDineSykmeldteHendelseType().toString(),
            lenke = null,
            tekst = varseltekst,
            utlopstidspunkt = OffsetDateTime.now().plusWeeks(4L)
        )
        senderFacade.sendTilDineSykmeldte(varselHendelse, dineSykmeldteVarsel)
    }
}
