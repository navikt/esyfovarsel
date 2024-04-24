package no.nav.syfo.service

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.arbeidstakerAktorId1
import no.nav.syfo.db.domain.Kanal
import no.nav.syfo.db.domain.PUtsendtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.setUtsendtVarselToFerdigstilt
import no.nav.syfo.db.storeUtsendtVarsel
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.kafka.producers.dittsykefravaer.DittSykefravaerMeldingKafkaProducer
import no.nav.syfo.planner.arbeidstakerFnr1
import no.nav.syfo.testutil.EmbeddedDatabase
import java.time.LocalDateTime
import java.util.*

class SenderFacadeSpek : DescribeSpec({
    describe("SenderFacadeSpek") {
        val embeddedDatabase = EmbeddedDatabase()

        val arbeidsgiverNotifikasjonService: ArbeidsgiverNotifikasjonService = mockk(relaxed = true)
        val dineSykmeldteHendelseKafkaProducer: DineSykmeldteHendelseKafkaProducer = mockk(relaxed = true)
        val brukernotifikasjonerService: BrukernotifikasjonerService = mockk(relaxed = true)
        val dittSykefravaerMeldingKafkaProducer: DittSykefravaerMeldingKafkaProducer = mockk(relaxed = true)
        val fysiskBrevUtsendingService: FysiskBrevUtsendingService = mockk(relaxed = true)

        val senderFacade = SenderFacade(
            dineSykmeldteHendelseKafkaProducer,
            dittSykefravaerMeldingKafkaProducer,
            brukernotifikasjonerService,
            arbeidsgiverNotifikasjonService,
            fysiskBrevUtsendingService,
            embeddedDatabase
        )

        val eksternRefArbeidsgiverNotifikasjoner = "arbeidsgivernotifikasjoner"
        val eksternRefDineSykmeldte = "dine_sykmeldte"
        val eksternRefBrukernotifikasjoner = "brukernotifikasjoner"
        val eksternRefDittSykefravaer = "ditt_sykefravær"
        val merkelapp = "merkelapp"

        val utsendtVarsel =
            PUtsendtVarsel(
                uuid = UUID.randomUUID().toString(),
                fnr = arbeidstakerFnr1,
                aktorId = arbeidstakerAktorId1,
                utsendtTidspunkt = LocalDateTime.now(),
                type = VarselType.MER_VEILEDNING.name,
                narmesteLederFnr = null,
                orgnummer = null,
                kanal = null,
                planlagtVarselId = null,
                eksternReferanse = null,
                ferdigstiltTidspunkt = null,
                arbeidsgivernotifikasjonMerkelapp = null,
            )

        val arbeidsgivernotifikasjonUtsendtVarsel = utsendtVarsel.copy(
            uuid = UUID.randomUUID().toString(),
            kanal = Kanal.ARBEIDSGIVERNOTIFIKASJON.name,
            eksternReferanse = eksternRefArbeidsgiverNotifikasjoner,
            arbeidsgivernotifikasjonMerkelapp = merkelapp
        )

        val dineSykmeldteUtsendtVarsel = utsendtVarsel.copy(
            uuid = UUID.randomUUID().toString(),
            kanal = Kanal.DINE_SYKMELDTE.name,
            eksternReferanse = eksternRefDineSykmeldte
        )

        val brukernotifikasjonUtsendtVarsel = utsendtVarsel.copy(
            uuid = UUID.randomUUID().toString(),
            kanal = Kanal.BRUKERNOTIFIKASJON.name,
            eksternReferanse = eksternRefBrukernotifikasjoner
        )

        val dittSykefravaerUtsendtVarsel = utsendtVarsel.copy(
            uuid = UUID.randomUUID().toString(),
            kanal = Kanal.DITT_SYKEFRAVAER.name,
            eksternReferanse = eksternRefDittSykefravaer,
        )

        beforeTest {
            clearAllMocks()
            embeddedDatabase.dropData()
        }

        it("Complete notifications for user") {

            embeddedDatabase.storeUtsendtVarsel(arbeidsgivernotifikasjonUtsendtVarsel)
            embeddedDatabase.storeUtsendtVarsel(dineSykmeldteUtsendtVarsel)
            embeddedDatabase.storeUtsendtVarsel(brukernotifikasjonUtsendtVarsel)
            embeddedDatabase.storeUtsendtVarsel(dittSykefravaerUtsendtVarsel)

            senderFacade.ferdigstillVarslerForFnr(PersonIdent(arbeidstakerFnr1))

            coVerify(exactly = 1) { arbeidsgiverNotifikasjonService.deleteNotifikasjon(merkelapp = merkelapp, eksternReferanse = eksternRefArbeidsgiverNotifikasjoner) }
            verify(exactly = 1) { dineSykmeldteHendelseKafkaProducer.ferdigstillVarsel(eksternReferanse = eksternRefDineSykmeldte) }
            verify(exactly = 1) { brukernotifikasjonerService.ferdigstillVarsel(uuid = eksternRefBrukernotifikasjoner) }
            verify(exactly = 1) { dittSykefravaerMeldingKafkaProducer.ferdigstillMelding(eksternReferanse = eksternRefDittSykefravaer, fnr = arbeidstakerFnr1) }
        }

        it("Don't complete notifications for user when they are already completed") {

            embeddedDatabase.storeUtsendtVarsel(arbeidsgivernotifikasjonUtsendtVarsel)
            embeddedDatabase.storeUtsendtVarsel(dineSykmeldteUtsendtVarsel)
            embeddedDatabase.storeUtsendtVarsel(brukernotifikasjonUtsendtVarsel)
            embeddedDatabase.storeUtsendtVarsel(dittSykefravaerUtsendtVarsel)
            embeddedDatabase.setUtsendtVarselToFerdigstilt(eksternRefArbeidsgiverNotifikasjoner)
            embeddedDatabase.setUtsendtVarselToFerdigstilt(eksternRefDineSykmeldte)
            embeddedDatabase.setUtsendtVarselToFerdigstilt(eksternRefBrukernotifikasjoner)
            embeddedDatabase.setUtsendtVarselToFerdigstilt(eksternRefDittSykefravaer)

            senderFacade.ferdigstillVarslerForFnr(PersonIdent(arbeidstakerFnr1))

            coVerify(exactly = 0) { arbeidsgiverNotifikasjonService.deleteNotifikasjon(any(), any()) }
            verify(exactly = 0) { dineSykmeldteHendelseKafkaProducer.ferdigstillVarsel(any()) }
            verify(exactly = 0) { brukernotifikasjonerService.ferdigstillVarsel(any()) }
            verify(exactly = 0) { dittSykefravaerMeldingKafkaProducer.ferdigstillMelding(any(), any()) }
        }
    }
})
