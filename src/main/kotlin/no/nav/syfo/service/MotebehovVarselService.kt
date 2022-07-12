package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_URL
import no.nav.syfo.DINE_SYKMELDTE_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.MotebehovNLVarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.consumers.varselbus.isOrgFnrNrValidFormat
import no.nav.syfo.kafka.consumers.varselbus.objectMapper
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.domain.DineSykmeldteVarsel
import org.apache.commons.cli.MissingArgumentException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class MotebehovVarselService(
    val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer,
    val brukernotifikasjonerService: BrukernotifikasjonerService,
    val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService,
    val narmesteLederService: NarmesteLederService,
    val dialogmoterUrl: String,
    val dineSykmeldteUrl: String,
) {
    val WEEKS_BEFORE_DELETE = 4L
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.MotebehovVarselService")

    suspend fun sendVarselTilNarmesteLeder(varselHendelse: EsyfovarselHendelse) {
        val varseldata = varselHendelse.dataToMotebehovNLVarselData()

        sendVarselTilDineSykmeldte(varselHendelse, varseldata)

        sendVarselTilArbeidsgiverNotifikasjon(varselHendelse, varseldata)
    }
    fun sendVarselTilArbeidstaker(fnr: String) {
        val url = URL(dialogmoterUrl + BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_URL)
        brukernotifikasjonerService.sendVarsel(UUID.randomUUID().toString(), fnr, BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST, url)
    }
    private suspend fun sendVarselTilArbeidsgiverNotifikasjon(varselHendelse: EsyfovarselHendelse, varseldata: MotebehovNLVarselData) {
        val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(varseldata.ansattFnr, varseldata.orgnummer)
        if (narmesteLederRelasjon !== null && narmesteLederService.hasNarmesteLederInfo(narmesteLederRelasjon)) {
            if (varselHendelse.mottakerFnr.equals(narmesteLederRelasjon.narmesteLederFnr)) {
                arbeidsgiverNotifikasjonService.sendNotifikasjon(
                    VarselType.SVAR_MOTEBEHOV,
                    null,
                    varseldata.orgnummer,
                    dineSykmeldteUrl + "/${narmesteLederRelasjon.narmesteLederId}",
                    narmesteLederRelasjon.narmesteLederFnr!!,
                    varseldata.ansattFnr,
                    narmesteLederRelasjon.narmesteLederEpost!!,
                    LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
                )
            } else {
                log.warn("Sender ikke varsel til ag-notifikasjon: den ansatte har nærmeste leder med annet fnr enn mottaker i varselHendelse")
            }
        } else {
            log.warn("Sender ikke varsel til ag-notifikasjon: narmesteLederRelasjon er null eller har ikke kontaktinfo")
        }
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
