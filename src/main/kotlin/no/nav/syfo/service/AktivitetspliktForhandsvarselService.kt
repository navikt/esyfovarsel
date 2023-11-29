package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_TEXT
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import no.nav.syfo.utils.dataToVarselData
import org.slf4j.LoggerFactory
import java.net.URL

class AktivitetspliktForhandsvarselVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
    val urlAktivitetskravInfoPage: String,
    private val isSendingEnabled: Boolean,
) {
    suspend fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        if (isSendingEnabled) {
            log.info("[FORHAANDSVARSEL] sending enabled")
            val data = dataToVarselData(varselHendelse.data)
            requireNotNull(data.aktivitetskrav)
            if (!data.aktivitetskrav.sendForhandsvarsel) {
                return
            }
            requireNotNull(data.journalpost)
            requireNotNull(data.journalpost.id)

            val userAccessStatus = accessControlService.getUserAccessStatus(varselHendelse.arbeidstakerFnr)
            if (userAccessStatus.canUserBeDigitallyNotified) {
                log.info("Sending [FORHAANDSVARSEL] to brukernotifikasjoner")
                senderFacade.sendTilBrukernotifikasjoner(
                    uuid = data.journalpost.uuid,
                    mottakerFnr = varselHendelse.arbeidstakerFnr,
                    content = BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_TEXT,
                    url = URL(urlAktivitetskravInfoPage),
                    varselHendelse = varselHendelse,
                    meldingType = BrukernotifikasjonKafkaProducer.MeldingType.OPPGAVE,
                    eksternVarsling = true,
                )
            } else {
                log.info("Sending [FORHAANDSVARSEL] to print")
                senderFacade.sendBrevTilFysiskPrint(
                    data.journalpost.uuid,
                    varselHendelse,
                    data.journalpost.id,
                    DistibusjonsType.VIKTIG,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AktivitetspliktForhandsvarselVarselService::class.java.canonicalName)
    }
}
