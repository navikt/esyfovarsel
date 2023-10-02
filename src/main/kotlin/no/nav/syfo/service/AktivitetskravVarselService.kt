package no.nav.syfo.service

import no.nav.syfo.BRUKERNOTIFIKASJONER_FORHANDSVARSEL_STANS_TEKST
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.SM_FORHANDSVARSEL_STANS
import no.nav.syfo.kafka.consumers.varselbus.domain.VarselData
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BrukernotifikasjonKafkaProducer
import org.apache.commons.cli.MissingArgumentException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL

class AktivitetskravVarselService(
    val senderFacade: SenderFacade,
    val accessControlService: AccessControlService,
    val isDevGcp: Boolean,
    val journalpostPageUrl: String,
) {
    private val log = LoggerFactory.getLogger(AktivitetskravVarselService::class.qualifiedName)

    fun sendVarselTilArbeidstaker(varselHendelse: ArbeidstakerHendelse) {
        if (isDevGcp) {
            sendVarselTilArbeidstaker2(varselHendelse);
        } else {
            require(varselHendelse.type == SM_FORHANDSVARSEL_STANS)
            val varselData = dataToVarselData(varselHendelse.data)

            requireNotNull(varselData.journalpost?.id)

            sendFysiskBrevTilArbeidstaker(varselData.journalpost!!.uuid, varselHendelse, varselData.journalpost.id!!)
        }
    }

    //Swap to this when our stuff is ready
    fun sendVarselTilArbeidstaker2(varselHendelse: ArbeidstakerHendelse) {
        require(varselHendelse.type == SM_FORHANDSVARSEL_STANS)
        val arbeidstakerFnr = varselHendelse.arbeidstakerFnr
        val varselData = dataToVarselData(varselHendelse.data)
        val userAccessStatus = accessControlService.getUserAccessStatus(arbeidstakerFnr)
        requireNotNull(varselData.journalpost?.id)

        if (userAccessStatus.canUserBeDigitallyNotified) {
            varsleArbeidstakerViaBrukernotifikasjoner(
                varselHendelse,
                varselData.journalpost!!.uuid,
                varselData.journalpost.id!!
            )
        } else {
            sendFysiskBrevTilArbeidstaker(varselData.journalpost!!.uuid, varselHendelse, varselData.journalpost.id!!)
        }
    }

    private fun sendFysiskBrevTilArbeidstaker(
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse,
        journalpostId: String,
    ) {
        try {
            senderFacade.sendBrevTilFysiskPrint(uuid, arbeidstakerHendelse, journalpostId)
        } catch (e: RuntimeException) {
            log.info("Feil i sending av fysisk brev om dialogmote: ${e.message} for hendelsetype: ${arbeidstakerHendelse.type.name}")
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

    private fun getVarselText(hendelseType: HendelseType): String {
        return when (hendelseType) {
            SM_FORHANDSVARSEL_STANS -> BRUKERNOTIFIKASJONER_FORHANDSVARSEL_STANS_TEKST
            else -> {
                throw IllegalArgumentException("Kan ikke mappe $hendelseType til arbeidstaker varsel text")
            }
        }
    }

    fun getVarselUrl(varselHendelse: ArbeidstakerHendelse, journalpostId: String): URL {
        if (SM_FORHANDSVARSEL_STANS === varselHendelse.type) {
            return URL("$journalpostPageUrl/$journalpostId")
        }
        throw IllegalArgumentException("Kan ikke mappe ${varselHendelse.type} til arbeidstaker varsel text")
    }

    private fun varsleArbeidstakerViaBrukernotifikasjoner(
        varselHendelse: ArbeidstakerHendelse,
        journalpostUuid: String,
        journalpostId: String
    ) {
        senderFacade.sendTilBrukernotifikasjoner(
            uuid = journalpostUuid,
            mottakerFnr = varselHendelse.arbeidstakerFnr,
            content = getVarselText(varselHendelse.type),
            url = getVarselUrl(varselHendelse, journalpostId),
            varselHendelse = varselHendelse,
            meldingType = BrukernotifikasjonKafkaProducer.MeldingType.BESKJED,
            eksternVarsling = true,
        )
    }

}
