package no.nav.syfo.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.job.urlPathJobTrigger
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.syfomotebehov.SyfoMotebehovConsumer
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.getTestEnv
import no.nav.syfo.job.VarselSender
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.service.AccessControlService
import no.nav.syfo.service.ArbeidsgiverNotifikasjonService
import no.nav.syfo.service.DokarkivService
import no.nav.syfo.service.SendVarselService
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
        val narmesteLederService = mockk<NarmesteLederService>()
        val syfoMotebeovConsumer = mockk<SyfoMotebehovConsumer>()
        val journalpostdistribusjonConsumer = mockk<JournalpostdistribusjonConsumer>()
        val dokarkivService = mockk<DokarkivService>()

        coEvery { accessControlService.getUserAccessStatusByFnr(fnr1) } returns userAccessStatus1
        coEvery { accessControlService.getUserAccessStatusByFnr(fnr2) } returns userAccessStatus2
        coEvery { accessControlService.getUserAccessStatusByFnr(fnr3) } returns userAccessStatus3
        coEvery { accessControlService.getUserAccessStatusByFnr(fnr4) } returns userAccessStatus4
        coEvery { accessControlService.getUserAccessStatusByFnr(fnr5) } returns userAccessStatus5
        coEvery { accessControlService.getUserAccessStatusByAktorId(aktorId) } returns userAccessStatus1
        coEvery { accessControlService.getUserAccessStatusByAktorId(aktorId2) } returns userAccessStatus2
        coEvery { accessControlService.getUserAccessStatusByAktorId(aktorId3) } returns userAccessStatus3
        coEvery { accessControlService.getUserAccessStatusByAktorId(aktorId4) } returns userAccessStatus4
        coEvery { accessControlService.getUserAccessStatusByAktorId(aktorId5) } returns userAccessStatus5

        coEvery { beskjedKafkaProducer.sendBeskjed(any(), any(), any(), any()) } returns Unit
        coEvery { dokarkivService.getJournalpostId(any(), any()) } returns "1"

        val sendVarselService =
            SendVarselService(
                beskjedKafkaProducer,
                dineSykmeldteHendelseKafkaProducer,
                narmesteLederService,
                accessControlService,
                testEnv.urlEnv,
                testEnv.appEnv,
                syfoMotebeovConsumer,
                arbeidsgiverNotifikasjonService,
                journalpostdistribusjonConsumer,
                dokarkivService,
            )
        val varselSender = VarselSender(embeddedDatabase, sendVarselService, testEnv.toggleEnv)

        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation, contentNegotationFeature())
            application.routing {
                registerJobTriggerApi(varselSender)
            }

            it("esyfovarsel-job trigger utsending av 2 varsler digitalt og 1 varsel som brev") {
                listOf(
                    PlanlagtVarsel(fnr1, aktorId, orgnummer, setOf("1"), VarselType.MER_VEILEDNING), // Blir sendt digitalt
                    PlanlagtVarsel(fnr2, aktorId2, orgnummer, setOf("2"), VarselType.AKTIVITETSKRAV), // Blir sendt digitalt
                    PlanlagtVarsel(fnr2, aktorId2, orgnummer, setOf("3"), VarselType.MER_VEILEDNING, LocalDate.now().plusDays(1)), // Blir ikke sendt pga dato
                    PlanlagtVarsel(fnr3, aktorId3, orgnummer, setOf("4"), VarselType.AKTIVITETSKRAV), // Blir ikke sendt, mottaker er reservert mot digital kommunikasjon
                    PlanlagtVarsel(fnr4, aktorId4, orgnummer, setOf("5"), VarselType.MER_VEILEDNING), // Blir sendt som brev
                    PlanlagtVarsel(fnr5, aktorId5, orgnummer, setOf("6"), VarselType.MER_VEILEDNING), // Blir ikke sendt, mottaker er reservert mot digital kommunikasjon og har kode 6 eller 7
                ).forEach { embeddedDatabase.storePlanlagtVarsel(it) }

                with(handleRequest(HttpMethod.Post, urlPathJobTrigger)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    verify(exactly = 2) { beskjedKafkaProducer.sendBeskjed(any(), any(), any(), any()) }
                    verify(exactly = 1) { runBlocking { dokarkivService.getJournalpostId(any(), any()) } }
                }
            }
        }
    }
})
