package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.PUtsendtVarselFeilet
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchMikrofrontendSynlighetEntriesByFnr
import no.nav.syfo.db.fetchUtsendtVarselByFnr
import no.nav.syfo.db.fetchUtsendtVarselFeiletByFnr
import no.nav.syfo.db.storeMikrofrontendSynlighetEntry
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.db.storeUtsendtVarselFeilet
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.mineside_microfrontend.MikrofrontendSynlighet
import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import no.nav.syfo.planner.ARBEIDSTAKER_FNR_1
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.testutil.EmbeddedDatabase
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TestdataResetServiceSpek :
    DescribeSpec({
        describe("TestdataResetServiceSpek") {
            val embeddedDatabase = EmbeddedDatabase()
            val mikrofrontendService: MikrofrontendService = mockk(relaxed = true)
            val senderFacade: SenderFacade = mockk(relaxed = true)
            val testdataResetService = TestdataResetService(embeddedDatabase, mikrofrontendService, senderFacade)

            val utsendtVarsel =
                PUtsendtVarsel(
                    uuid = UUID.randomUUID().toString(),
                    fnr = ARBEIDSTAKER_FNR_1,
                    aktorId = no.nav.syfo.db.ARBEIDSTAKER_AKTOR_ID_1,
                    narmesteLederFnr = null,
                    orgnummer = null,
                    type = VarselType.MER_VEILEDNING.name,
                    kanal = null,
                    utsendtTidspunkt = LocalDateTime.now(),
                    planlagtVarselId = null,
                    eksternReferanse = null,
                    ferdigstiltTidspunkt = null,
                    arbeidsgivernotifikasjonMerkelapp = null,
                    isForcedLetter = false,
                    journalpostId = null,
                )

            val pUtsendtVarselFeilet =
                PUtsendtVarselFeilet(
                    UUID.randomUUID().toString(),
                    null,
                    ARBEIDSTAKER_FNR_1,
                    no.nav.syfo.planner.ARBEIDSTAKER_AKTOR_ID_1,
                    null,
                    HendelseType.SM_DIALOGMOTE_SVAR_MOTEBEHOV.name,
                    null,
                    null,
                    null,
                    Kanal.BRUKERNOTIFIKASJON.name,
                    null,
                    LocalDateTime.now(),
                    false,
                )

            val mikrofrontendSynlighet =
                MikrofrontendSynlighet(ARBEIDSTAKER_FNR_1, Tjeneste.DIALOGMOTE, LocalDate.now().plusWeeks(1))

            beforeTest {
                embeddedDatabase.dropData()
            }

            it("Reset all testdata") {
                embeddedDatabase.storeUtsendtVarsel(utsendtVarsel)
                embeddedDatabase.storeUtsendtVarselFeilet(pUtsendtVarselFeilet)
                embeddedDatabase.storeMikrofrontendSynlighetEntry(mikrofrontendSynlighet)

                // Verify that testdata exists
                embeddedDatabase.fetchUtsendtVarselByFnr(ARBEIDSTAKER_FNR_1).size shouldBeEqualTo 1
                embeddedDatabase.fetchUtsendtVarselFeiletByFnr(ARBEIDSTAKER_FNR_1).size shouldBeEqualTo 1
                embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(ARBEIDSTAKER_FNR_1).size shouldBeEqualTo 1

                testdataResetService.resetTestdata(PersonIdent(ARBEIDSTAKER_FNR_1))

                // Check that testdata is deleted
                embeddedDatabase.fetchUtsendtVarselByFnr(ARBEIDSTAKER_FNR_1).size shouldBeEqualTo 0
                embeddedDatabase.fetchUtsendtVarselFeiletByFnr(ARBEIDSTAKER_FNR_1).size shouldBeEqualTo 0
                embeddedDatabase.fetchMikrofrontendSynlighetEntriesByFnr(ARBEIDSTAKER_FNR_1).size shouldBeEqualTo 0
                verify(exactly = 1) {
                    mikrofrontendService.closeAllMikrofrontendForUser(PersonIdent(ARBEIDSTAKER_FNR_1))
                }
                coVerify(exactly = 1) {
                    senderFacade.ferdigstillVarslerForFnr(PersonIdent(ARBEIDSTAKER_FNR_1))
                }
            }
        }
    })
