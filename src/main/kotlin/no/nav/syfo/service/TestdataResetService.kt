package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.deleteMikrofrontendSynlighetByFnr
import no.nav.syfo.db.deleteUtsendtVarselByFnr
import no.nav.syfo.db.deleteUtsendtVarselFeiletByFnr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.service.microfrontend.MikrofrontendService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TestdataResetService(
    private val database: DatabaseInterface,
    private val mikrofrontendService: MikrofrontendService,
    private val senderFacade: SenderFacade,
) {

    private val log: Logger = LoggerFactory.getLogger(TestdataResetService::class.qualifiedName)
    suspend fun resetTestdata(fnr: PersonIdent) {
        log.info(
            "Nullstiller testdata for arbeidstaker ${fnr.value}. Ferdigstiller varsler " +
                    "som er sendt til Min side - arbeidsgiver, Dine sykmeldte, Min side (personbruker), " +
                    "Ditt sykefravær. Fjerner mikrofrontend på Min side. " +
                    "Sletter planlagte og utsendte varsler, feilede varsler."
        )

        // Ferdigstille varsler
        senderFacade.ferdigstillVarslerForFnr(fnr)

        // Slette utsendte varsler
        database.deleteUtsendtVarselByFnr(fnr)

        // Slette/skjule mikrofrontend
        mikrofrontendService.closeAllMikrofrontendForUser(fnr)
        database.deleteMikrofrontendSynlighetByFnr(fnr)

        // Slette utsendt varsel feilet
        database.deleteUtsendtVarselFeiletByFnr(fnr)
    }
}
