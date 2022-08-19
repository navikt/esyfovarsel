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
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.consumer.syfomotebehov.SyfoMotebehovConsumer
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.getTestEnv
import no.nav.syfo.job.VarselSender
import no.nav.syfo.kafka.producers.brukernotifikasjoner.BeskjedKafkaProducer
import no.nav.syfo.kafka.producers.dinesykmeldte.DineSykmeldteHendelseKafkaProducer
import no.nav.syfo.service.AccessControl
import no.nav.syfo.service.ArbeidsgiverNotifikasjonService
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
        val accessControl = mockk<AccessControl>()
        val beskjedKafkaProducer = mockk<BeskjedKafkaProducer>()
        val arbeidsgiverNotifikasjonService = mockk<ArbeidsgiverNotifikasjonService>()
        val dineSykmeldteHendelseKafkaProducer = mockk<DineSykmeldteHendelseKafkaProducer>()
        val narmesteLederService = mockk<NarmesteLederService>()
        val syfoMotebeovConsumer = mockk<SyfoMotebehovConsumer>()

        coEvery { accessControl.canUserBeNotified(fnr1) } returns true
        coEvery { accessControl.canUserBeNotified(fnr2) } returns true
        coEvery { accessControl.canUserBeNotified(fnr3) } returns false

        coEvery { beskjedKafkaProducer.sendBeskjed(any(), any(), any(), any()) } returns Unit

        val sendVarselService =
            SendVarselService(
                beskjedKafkaProducer,
                dineSykmeldteHendelseKafkaProducer,
                narmesteLederService,
                accessControl,
                testEnv.urlEnv,
                syfoMotebeovConsumer,
                arbeidsgiverNotifikasjonService,
            )
        val varselSender = VarselSender(embeddedDatabase, sendVarselService, testEnv.toggleEnv)

        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation, contentNegotationFeature())
            application.routing {
                registerJobTriggerApi(varselSender)
            }

            it("esyfovarsel-job trigger utsending av 2 varsler") {
                listOf(
                    PlanlagtVarsel(fnr1, aktorId, orgnummer, setOf("1"), VarselType.MER_VEILEDNING),
                    PlanlagtVarsel(fnr2, aktorId2, orgnummer, setOf("2"), VarselType.AKTIVITETSKRAV),
                    PlanlagtVarsel(fnr2, aktorId2, orgnummer, setOf("3"), VarselType.MER_VEILEDNING, LocalDate.now().plusDays(1)),
                    PlanlagtVarsel(fnr3, aktorId3, orgnummer, setOf("4"), VarselType.AKTIVITETSKRAV)
                ).forEach { embeddedDatabase.storePlanlagtVarsel(it) }

                with(handleRequest(HttpMethod.Post, urlPathJobTrigger)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    verify(exactly = 2) { beskjedKafkaProducer.sendBeskjed(any(), any(), any(), any()) }
                }
            }
        }
    }
})
