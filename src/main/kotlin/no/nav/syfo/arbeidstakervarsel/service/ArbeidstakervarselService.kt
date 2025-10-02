package no.nav.syfo.arbeidstakervarsel.service

import no.nav.syfo.arbeidstakervarsel.dao.ArbeidstakervarselDao
import no.nav.syfo.arbeidstakervarsel.domain.ArbeidstakerVarsel

class ArbeidstakervarselService(
    private val brukernotifikasjonService: BrukernotifikasjonService,
    private val arbeidstakervarselDao: ArbeidstakervarselDao,
    private val dokumentDistribusjonService: DokumentDistribusjonService,
    private val dittSykefravaerVarselService: DittSykefravaerVarselService,
    private val mikrofrontendService: MikrofrontendService

) {
    suspend fun processVarsel(arbeidstakerVarsel: ArbeidstakerVarsel) {
        arbeidstakervarselDao.storeArbeidstakerVarselHendelse(arbeidstakerVarsel)

        if (arbeidstakerVarsel.brukernotifikasjonVarsel != null) {
            brukernotifikasjonService.sendBrukernotifikasjon(
                mottakerFnr = arbeidstakerVarsel.mottakerFnr,
                brukernotifikasjonVarsel = arbeidstakerVarsel.brukernotifikasjonVarsel
            ).also {
                arbeidstakervarselDao.storeSendResult(it)
            }
        }

        if (arbeidstakerVarsel.dokumentDistribusjonVarsel != null) {
            dokumentDistribusjonService.distribuerJournalpost(
                dokumentDistribusjonVarsel = arbeidstakerVarsel.dokumentDistribusjonVarsel
            ).also { arbeidstakervarselDao.storeSendResult(it) }
        }

        if (arbeidstakerVarsel.dittSykefravaerVarsel != null) {
            dittSykefravaerVarselService.sendVarsel(
                arbeidstakerVarsel.mottakerFnr,
                arbeidstakerVarsel.dittSykefravaerVarsel
            ).also { arbeidstakervarselDao.storeSendResult(it) }
        }

        if (arbeidstakerVarsel.microfrontend != null) {
            mikrofrontendService.updateMikrofrontendForUserByHendelse(
                mottakerFnr = arbeidstakerVarsel.mottakerFnr,
                hendelse = arbeidstakerVarsel.microfrontend
            )
        }
    }
}
