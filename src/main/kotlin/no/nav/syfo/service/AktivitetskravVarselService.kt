package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST
import no.nav.syfo.kafka.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.varselbus.domain.AktivitetskravNLVarselData
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.varselbus.isOrgFnrNrValidFormat
import no.nav.syfo.kafka.varselbus.objectMapper
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException
import java.time.OffsetDateTime

class AktivitetskravVarselService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer
) {
    fun sendVarselTilDineSykmeldte(varselHendelse: EsyfovarselHendelse) {
        val varseltekst = DINE_SYKMELDTE_AKTIVITETSKRAV_TEKST
        val varseldata = varselHendelse.dataToAktivitetskravNLVarselData()
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

fun EsyfovarselHendelse.dataToAktivitetskravNLVarselData(): AktivitetskravNLVarselData {
    return data?.let {
        try {
            val varseldata: AktivitetskravNLVarselData = objectMapper.readValue(data.toString())
            if (isOrgFnrNrValidFormat(varseldata.ansattFnr, varseldata.orgnummer)) {
                return@let varseldata
            }
            throw IllegalArgumentException("MotebehovNLVarselData har ugyldig fnr eller orgnummer")
        } catch (e: IOException) {
            throw IOException("EsyfovarselHendelse har feil format i 'data'-felt")
        }
    } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
}
