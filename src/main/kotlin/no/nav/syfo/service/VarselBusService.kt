package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.kafka.consumers.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.consumers.varselbus.isOrgFnrNrValidFormat
import no.nav.syfo.kafka.consumers.varselbus.objectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.Serializable

class VarselBusService(
    val motebehovVarselService: MotebehovVarselService,
    val oppfolgingsplanVarselService: OppfolgingsplanVarselService
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.VarselBusService")

    fun processVarselHendelse(varselHendelse: EsyfovarselHendelse) {
        log.info("Behandler varsel av type ${varselHendelse.type}")
        varselHendelse.berikMedAnsattfnrOgOrgnummer()
        when (varselHendelse.type) {
            NL_OPPFOLGINGSPLAN_OPPRETTET,
            NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING -> oppfolgingsplanVarselService.sendVarselTilDineSykmeldte(varselHendelse)
            NL_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilNarmesteLeder(varselHendelse)
            SM_DIALOGMOTE_SVAR_MOTEBEHOV -> motebehovVarselService.sendVarselTilSykmeldt(varselHendelse)
        }
    }

    /** Fjerner denne når klientene har begynt å sende ansattFnr og orgummer direkte i EsyfovarselHendelse */
    fun EsyfovarselHendelse.berikMedAnsattfnrOgOrgnummer() {
        data?.let {
            try {
                val varseldata: VarselData = objectMapper.readValue(data.toString())
                if (isOrgFnrNrValidFormat(varseldata.ansattFnr, varseldata.orgnummer)) {
                    ansattFnr = varseldata.ansattFnr
                    orgnummer = varseldata.orgnummer
                }
            } catch (e: IOException) {
                log.info("EsyfovarselHendelse har feil format i 'data'-felt")
            }
        } ?: log.info("EsyfovarselHendelse mangler 'data'-felt")
    }

    data class VarselData(
        val ansattFnr: String,
        val orgnummer: String
    ) : Serializable

}
