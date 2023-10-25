package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_TEXT
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.apache.commons.cli.MissingArgumentException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.util.*

class AktivitetspliktForhandsvarselVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
    val journalpostPageUrl: String,
    private val isSendingEnabled: Boolean,
) {
    fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        // TODO: OBS, VIKTIG!
        // TODO:  Vi har ikke lov til å sende aktivtetskrav-varsel som en beskjed.
        // TODO:  Det må implementeres som en oppgave utsending før vi kan skru
        // TODO:  på denne funksjonaliteten. Da må vi også finne ut hvordan vi
        // TODO:  lukker oppgaven når bruker har utført/lest den
        if (isSendingEnabled) {
            log.info("[FORHAANDSVARSEL] sending enabled")
            val userAccessStatus = accessControlService.getUserAccessStatus(varselHendelse.arbeidstakerFnr)
            val varselData = dataToVarselData(varselHendelse.data)
            requireNotNull(varselData.journalpost)
            requireNotNull(varselData.journalpost.id)

            if (userAccessStatus.canUserBeDigitallyNotified) {
                log.info("Sending [FORHAANDSVARSEL] to brukernotifikasjoner")
                senderFacade.sendTilBrukernotifikasjoner(
                    uuid = varselData.journalpost.uuid,
                    mottakerFnr = varselHendelse.arbeidstakerFnr,
                    content = BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_TEXT,
                    url = URL("$journalpostPageUrl/${varselData.journalpost.id}"),
                    varselHendelse = varselHendelse,
                    meldingType = BrukernotifikasjonKafkaProducer.MeldingType.BESKJED,
                    eksternVarsling = true,
                )
            } else {
                log.info("Sending [FORHAANDSVARSEL] to print")
                senderFacade.sendBrevTilFysiskPrint(
                    varselData.journalpost.uuid,
                    varselHendelse,
                    varselData.journalpost.id,
                    DistibusjonsType.VIKTIG,
                )
            }
        }
    }

    private fun dataToVarselData(data: Any?): VarselData {
        return data?.let {
            try {
                return data.toVarselData()
            } catch (e: IOException) {
                throw IOException("ArbeidstakerHendelse har feil format")
            }
        } ?: throw MissingArgumentException("EsyfovarselHendelse mangler 'data'-felt")
    }

    companion object {
        private val log = LoggerFactory.getLogger(AktivitetspliktForhandsvarselVarselService::class.java)
    }
}
