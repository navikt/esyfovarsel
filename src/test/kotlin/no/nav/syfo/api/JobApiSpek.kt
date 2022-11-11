package no.nav.syfo.api

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.job.urlPathJobTrigger
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.getTestEnv
import no.nav.syfo.job.VarselSender
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.service.*
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.mocks.*
import no.nav.syfo.util.contentNegotationFeature
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object JobApiSpek : Spek({

    defaultTimeout = 20000L

    val testEnv = getTestEnv()

    describe("JobTriggerApi test") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val accessControlService = mockk<AccessControlService>()
        val beskjedKafkaProducer = mockk<BeskjedKafkaProducer>()
        val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>()
        val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
        val dokarkivService = mockk<DokarkivService>()
        val merVeiledningVarselService = mockk<MerVeiledningVarselService>()
        val sykmeldingService = mockk<SykmeldingService>()

        coEvery { accessControlService.getUserAccessStatus(fnr1) } returns userAccessStatus1
        coEvery { accessControlService.getUserAccessStatus(fnr2) } returns userAccessStatus2
        coEvery { accessControlService.getUserAccessStatus(fnr3) } returns userAccessStatus3
        coEvery { accessControlService.getUserAccessStatus(fnr4) } returns userAccessStatus4
        coEvery { accessControlService.getUserAccessStatus(fnr5) } returns userAccessStatus5
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
                sykmeldingService
            )
        val varselSenderService = VarselSenderService(
            embeddedDatabase,
            sykmeldingService
        )
        val varselSender = VarselSender(embeddedDatabase, sendVarselService, varselSenderService, testEnv.toggleEnv)

        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation, contentNegotationFeature())
            application.routing {
                registerJobTriggerApi(varselSender)
            }

            it("esyfovarsel-job trigger utsending av 2 varsler digitalt og 1 varsel som brev") {
                listOf(
                    PlanlagtVarsel(
                        fnr1,
                        aktorId,
                        orgnummer,
                        setOf("1"),
                        VarselType.MER_VEILEDNING
                    ), // Blir sendt digitalt
                    PlanlagtVarsel(
                        fnr2,
                        aktorId2,
                        orgnummer,
                        setOf("2"),
                        VarselType.AKTIVITETSKRAV
                    ), // Blir sendt digitalt
                    PlanlagtVarsel(
                        fnr2,
                        aktorId2,
                        orgnummer,
                        setOf("3"),
                        VarselType.MER_VEILEDNING,
                        LocalDate.now().plusDays(1)
                    ), // Blir ikke sendt pga dato
                    PlanlagtVarsel(
                        fnr3,
                        aktorId3,
                        orgnummer,
                        setOf("4"),
                        VarselType.AKTIVITETSKRAV
                    ), // Blir ikke sendt, mottaker er reservert mot digital kommunikasjon
                    PlanlagtVarsel(
                        fnr4,
                        aktorId4,
                        orgnummer,
                        setOf("5"),
                        VarselType.MER_VEILEDNING
                    ), // Blir sendt som brev
                    PlanlagtVarsel(
                        fnr5,
                        aktorId5,
                        orgnummer,
                        setOf("6"),
                        VarselType.MER_VEILEDNING
                    ), // Blir ikke sendt, mottaker er reservert mot digital kommunikasjon og har kode 6 eller 7
                ).forEach { embeddedDatabase.storePlanlagtVarsel(it) }

                with(handleRequest(HttpMethod.Post, urlPathJobTrigger)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    verify(exactly = 2) { merVeiledningVarselService.sendVarselTilArbeidstaker(any(), any(), any()) }
                    verify(exactly = 1) { beskjedKafkaProducer.sendBeskjed(any(), any(), any(), any()) }
                }
            }
        }
    }
})
