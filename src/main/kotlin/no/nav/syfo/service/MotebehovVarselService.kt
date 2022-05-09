package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.DINE_SYKMELDTE_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.kafka.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.MotebehovNLVarselData
import no.nav.syfo.kafka.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.varselbus.isOrgFnrNrValidFormat
import no.nav.syfo.kafka.varselbus.objectMapper
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException
import java.time.OffsetDateTime

class MotebehovVarselService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer
) {
    fun sendVarselTilDineSykmeldte(varselHendelse: EsyfovarselHendelse) {
        val varseltekst = DINE_SYKMELDTE_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
        val varseldata = varselHendelse.dataToMotebehovNLVarselData()
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

fun EsyfovarselHendelse.dataToMotebehovNLVarselData(): MotebehovNLVarselData {
    return data?.let {
        try {
            val varseldata: MotebehovNLVarselData = objectMapper.readValue(data.toString())
            if (isOrgFnrNrValidFormat(varseldata.ansattFnr, varseldata.orgnummer)) {
                return@let varseldata
            }
            throw IllegalArgumentException("MotebehovNLVarselData har ugyldig fnr eller orgnummer")
        } catch (e: IOException) {
            throw IOException("EsyfovarselHendelse har feil format i 'data'-felt")
        }
    } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
}
