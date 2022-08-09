package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.*
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.MotebehovNLVarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.consumers.varselbus.isOrgFnrNrValidFormat
import no.nav.syfo.kafka.consumers.varselbus.objectMapper
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class MotebehovVarselService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val brukernotifikasjonerService: BrukernotifikasjonerService,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val dialogmoterUrl: String,
) {
    val WEEKS_BEFORE_DELETE = 4L

    fun sendVarselTilNarmesteLeder(varselHendelse: EsyfovarselHendelse) {
        val varseldata = varselHendelse.dataToMotebehovNLVarselData()

        sendVarselTilDineSykmeldte(varselHendelse, varseldata)

        sendVarselTilArbeidsgiverNotifikasjon(varselHendelse, varseldata)
    }
    fun sendVarselTilSykmeldt(varselHendelse: EsyfovarselHendelse) {
        val url = URL(dialogmoterUrl + BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_URL)
        brukernotifikasjonerService.sendVarsel(UUID.randomUUID().toString(), varselHendelse.mottakerFnr, BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST, url)
    }
    private fun sendVarselTilArbeidsgiverNotifikasjon(varselHendelse: EsyfovarselHendelse, varseldata: MotebehovNLVarselData) {
                arbeidsgiverNotifikasjonService.sendNotifikasjon(
                    ArbeidsgiverNotifikasjonInput(
                        UUID.randomUUID(),
                        varseldata.orgnummer,
                        varselHendelse.mottakerFnr,
                        varseldata.ansattFnr,
                        ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_MESSAGE_TEXT,
                        ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_TITLE,
                        { url: String -> ARBEIDSGIVERNOTIFIKASJON_SVAR_MOTEBEHOV_EMAIL_BODY},
                        LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)))
    }

    private fun sendVarselTilDineSykmeldte(varselHendelse: EsyfovarselHendelse, varseldata: MotebehovNLVarselData) {
        val varseltekst = DINE_SYKMELDTE_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
        val dineSykmeldteVarsel = DineSykmeldteVarsel(
            varseldata.ansattFnr,
            varseldata.orgnummer,
            varselHendelse.type.toDineSykmeldteHendelseType().toString(),
            null,
            varseltekst,
            OffsetDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE)
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
