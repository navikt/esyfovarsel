package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.DINE_SYKMELDTE_OPPFOLGINGSPLAN_OPPRETTET_TEKST
import no.nav.syfo.DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST
import no.nav.syfo.kafka.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.varselbus.domain.OppfolgingsplanNLVarselData
import no.nav.syfo.kafka.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.varselbus.isOrgFnrNrValidFormat
import no.nav.syfo.kafka.varselbus.objectMapper
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException
import java.time.OffsetDateTime

class OppfolgingsplanVarselService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer
) {
    fun sendVarselTilDineSykmeldte(varselHendelse: EsyfovarselHendelse) {
        val varseltekst = when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> DINE_SYKMELDTE_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST
            NL_OPPFOLGINGSPLAN_OPPRETTET -> DINE_SYKMELDTE_OPPFOLGINGSPLAN_OPPRETTET_TEKST
            else -> {throw IllegalArgumentException("Type må være Oppfølgingsplan-type")}
        }
        val varseldata = varselHendelse.dataToOppfolgingsplanNLVarselData()
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            varseldata.ansattFnr,
            varseldata.orgnummer,
            varselHendelse.type.toDineSykmeldteHendelseType().toString(),
            null,
            varseltekst,
            OffsetDateTime.now().plusWeeks(4L)
        )
        dineSykmeldteHendelseKafkaProducer.sendVarsel(dineSykmeldteVarsel)
    }
}

fun EsyfovarselHendelse.dataToOppfolgingsplanNLVarselData(): OppfolgingsplanNLVarselData {
    return data?.let {
        try {
            val varseldata: OppfolgingsplanNLVarselData = objectMapper.readValue(data.toString())
            if (isOrgFnrNrValidFormat(varseldata.ansattFnr, varseldata.orgnummer)) {
                return@let varseldata
            }
            throw IllegalArgumentException("OppfolgingsplanNLVarselData har ugyldig fnr eller orgnummer")
        } catch (e: IOException) {
            throw IOException("EsyfovarselHendelse har feil format i 'data'-felt")
        }
    } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
}
