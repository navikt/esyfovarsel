package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_TEXT
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException
import java.net.URL

class AktivitetspliktForhandsvarselVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
    val journalpostPageUrl: String,
    private val isSendingEnabled: Boolean,
) {
    fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        if (isSendingEnabled) {
            val userAccessStatus = accessControlService.getUserAccessStatus(varselHendelse.arbeidstakerFnr)
            val varselData = dataToVarselData(varselHendelse.data)
            requireNotNull(varselData.journalpost)
            requireNotNull(varselData.journalpost.id)

            if (userAccessStatus.canUserBeDigitallyNotified) {
                senderFacade.sendTilBrukernotifikasjoner(
                    uuid = varselData.journalpost.uuid,
                    mottakerFnr = varselHendelse.arbeidstakerFnr,
                    content = BRUKERNOTIFIKASJON_AKTIVITETSKRAV_FORHANDSVARSEL_STANS_TEXT,
                    url = URL("$journalpostPageUrl/${varselData.journalpost.id}"),
                    varselHendelse = varselHendelse,
                    meldingType = BrukernotifikasjonKafkaProducer.MeldingType.BESKJED,
                    eksternVarsling = true,
                )
            }
            senderFacade.sendBrevTilFysiskPrint(
                varselData.journalpost.uuid,
                varselHendelse,
                varselData.journalpost.id,
                DistibusjonsType.VIKTIG,
            )
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
}
