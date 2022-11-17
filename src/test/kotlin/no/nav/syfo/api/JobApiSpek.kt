package no.nav.syfo.api

import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.job.urlPathJobTrigger
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.getTestEnv
import no.nav.syfo.job.VarselSender
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.service.AccessControlService
import no.nav.syfo.service.ArbeidsgiverNotifikasjonService
import no.nav.syfo.service.DokarkivService
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.service.SendVarselService
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.service.SykmeldingStatus
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.fnr1
import no.nav.syfo.testutil.mocks.fnr2
import no.nav.syfo.testutil.mocks.fnr3
import no.nav.syfo.testutil.mocks.fnr4
import no.nav.syfo.testutil.mocks.fnr5
import no.nav.syfo.testutil.mocks.orgnummer
import no.nav.syfo.testutil.mocks.pdlPersonUnder67Years
import no.nav.syfo.testutil.mocks.userAccessStatus1
import no.nav.syfo.testutil.mocks.userAccessStatus2
import no.nav.syfo.testutil.mocks.userAccessStatus3
import no.nav.syfo.testutil.mocks.userAccessStatus4
import no.nav.syfo.testutil.mocks.userAccessStatus5
import no.nav.syfo.util.contentNegotationFeature
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object JobApiSpek : Spek({

    defaultTimeout = 20000L

    val testEnv = getTestEnv()

    describe("JobTriggerApi test") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val accessControlService = mockk<AccessControlService>()
        val beskjedKafkaProducer = mockk<BeskjedKafkaProducer>()
        val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>()
        val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
        val merVeiledningVarselFinder = mockk<MerVeiledningVarselFinder>(relaxed = true)
        val dokarkivService = mockk<DokarkivService>()
        val merVeiledningVarselService = mockk<MerVeiledningVarselService>()
        val sykmeldingService = mockk<SykmeldingService>()
        val pdlConsumer = mockk<PdlConsumer>()

        coEvery { pdlConsumer.hentPerson(any()) } returns pdlPersonUnder67Years
        coEvery { accessControlService.getUserAccessStatus(fnr1) } returns userAccessStatus1
        coEvery { accessControlService.getUserAccessStatus(fnr2) } returns userAccessStatus2
        coEvery { accessControlService.getUserAccessStatus(fnr3) } returns userAccessStatus3
        coEvery { accessControlService.getUserAccessStatus(fnr4) } returns userAccessStatus4
        coEvery { accessControlService.getUserAccessStatus(fnr5) } returns userAccessStatus5

        coEvery { accessControlService.getUserAccessStatus(fnr1) } returns userAccessStatus1
        coEvery { accessControlService.getUserAccessStatus(fnr2) } returns userAccessStatus2
        coEvery { accessControlService.getUserAccessStatus(fnr3) } returns userAccessStatus3
        coEvery { accessControlService.getUserAccessStatus(fnr4) } returns userAccessStatus4
        coEvery { accessControlService.getUserAccessStatus(fnr5) } returns userAccessStatus5

        coEvery { merVeiledningVarselFinder.findMerVeiledningVarslerToSendToday() } returns listOf(
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr1, // Blir sendt digitalt. Kan varsles digitalt
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
            ),
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr2, // Blir sendt digitalt. Kan varsles digitalt
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
            ),
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr3, // Blir sendt, kan varsles fysisk
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
            ),
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr4, // Blir sendt, mottaker kan varsles fysisk
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
            ),
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr5, // Blir ikke sendt, mottaker er reservert mot digital kommunikasjon og har kode 6 eller 7
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
            ),
        )
        coEvery {
            sykmeldingService.checkSykmeldingStatusForVirksomhet(
                any(),
                any(),
                any()
            )
        } returns SykmeldingStatus(isSykmeldtIJobb = false, sendtArbeidsgiver = true)
        coEvery { beskjedKafkaProducer.sendBeskjed(any(), any(), any(), any()) } returns Unit
        coEvery { dokarkivService.getJournalpostId(any(), any()) } returns "1"
        coEvery { sykmeldingService.isPersonSykmeldtPaDato(any(), any()) } returns true

        val sendVarselService =
            SendVarselService(
                beskjedKafkaProducer,
                dineSykmeldteHendelseKafkaProducer,
                accessControlService,
                testEnv.urlEnv,
                arbeidsgiverNotifikasjonService,
                merVeiledningVarselService,
                sykmeldingService,
                pdlConsumer,
            )
        val varselSender = VarselSender(embeddedDatabase, sendVarselService, merVeiledningVarselFinder, testEnv.toggleEnv)

        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation, contentNegotationFeature())
            application.routing {
                registerJobTriggerApi(varselSender)
            }

            it("esyfovarsel-job trigger utsending av 2 varsler digitalt og 2 varsler som brev") {
                with(handleRequest(HttpMethod.Post, urlPathJobTrigger)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    verify(exactly = 4) { merVeiledningVarselService.sendVarselTilArbeidstaker(any(), any(), any()) }
                }
            }
        }
    }
})
