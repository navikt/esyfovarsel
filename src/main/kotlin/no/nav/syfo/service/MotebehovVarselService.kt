package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_URL
import no.nav.syfo.DINE_SYKMELDTE_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.kafka.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.dinesykmeldte.domain.DineSykmeldteVarsel
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.MotebehovNLVarselData
import no.nav.syfo.kafka.varselbus.domain.toDineSykmeldteHendelseType
import no.nav.syfo.kafka.varselbus.isOrgFnrNrValidFormat
import no.nav.syfo.kafka.varselbus.objectMapper
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
    val narmesteLederService: NarmesteLederService,
    val dialogmoterUrl: String
) {
    val WEEKS_BEFORE_DELETE = 4L

    suspend fun sendVarselTilNarmesteLeder(varselHendelse: EsyfovarselHendelse) {
        val varseldata = varselHendelse.dataToMotebehovNLVarselData()

        sendVarselTilDineSykmeldte(varselHendelse, varseldata)

        val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(varseldata.ansattFnr, varseldata.orgnummer)
        if (narmesteLederRelasjon !== null && narmesteLederService.hasNarmesteLederInfo(narmesteLederRelasjon)) {
            arbeidsgiverNotifikasjonService.sendNotifikasjon(
                VarselType.SVAR_MOTEBEHOV,
                null,
                varseldata.orgnummer,
                dialogmoterUrl + "/arbeidsgiver/${narmesteLederRelasjon.narmesteLederId}",
                narmesteLederRelasjon.narmesteLederFnr!!,
                varseldata.ansattFnr,
                narmesteLederRelasjon.narmesteLederEpost!!,
                LocalDateTime.now().plusWeeks(WEEKS_BEFORE_DELETE),
            )
        }
    }

    fun sendVarselTilSykmeldt(varselHendelse: EsyfovarselHendelse) {
        val url = URL(dialogmoterUrl + BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_URL)
        brukernotifikasjonerService.sendVarsel(UUID.randomUUID().toString(), varselHendelse.mottakerFnr, BRUKERNOTIFIKASJONER_DIALOGMOTE_SVAR_MOTEBEHOV_TEKST, url)
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
