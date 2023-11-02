package no.nav.syfo.api

import io.kotest.core.spec.style.DescribeSpec
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.job.urlPathJobTrigger
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.getTestEnv
import no.nav.syfo.job.SendMerVeiledningVarslerJobb
import no.nav.syfo.service.AccessControlService
import no.nav.syfo.service.MerVeiledningVarselFinder
import no.nav.syfo.service.MerVeiledningVarselService
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.testutil.mocks.fnr1
import no.nav.syfo.testutil.mocks.fnr2
import no.nav.syfo.testutil.mocks.fnr3
import no.nav.syfo.testutil.mocks.fnr4
import no.nav.syfo.testutil.mocks.fnr5
import no.nav.syfo.testutil.mocks.orgnummer
import no.nav.syfo.testutil.mocks.userAccessStatus1
import no.nav.syfo.testutil.mocks.userAccessStatus2
import no.nav.syfo.testutil.mocks.userAccessStatus3
import no.nav.syfo.testutil.mocks.userAccessStatus4
import no.nav.syfo.testutil.mocks.userAccessStatus5
import no.nav.syfo.util.contentNegotationFeature
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class JobApiSpek : DescribeSpec({
    val testEnv = getTestEnv()

    timeout = 20000L

    describe("JobTriggerApi test") {
        val accessControlService = mockk<AccessControlService>()
        val merVeiledningVarselFinder = mockk<MerVeiledningVarselFinder>(relaxed = true)
        val merVeiledningVarselService = mockk<MerVeiledningVarselService>()
        val mikrofrontendService = mockk<MikrofrontendService>()

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
                LocalDateTime.now(),
            ),
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr2, // Blir sendt digitalt. Kan varsles digitalt
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
            ),
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr3, // Blir sendt, kan varsles fysisk
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
            ),
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr4, // Blir sendt, mottaker kan varsles fysisk
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
            ),
            PPlanlagtVarsel(
                UUID.randomUUID().toString(),
                fnr5, // Blir sendt, mottaker kan varsles fysisk
                orgnummer,
                null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
            ),
        )

        justRun { mikrofrontendService.findAndCloseExpiredMikrofrontends() }

        val sendMerVeiledningVarslerJobb =
            SendMerVeiledningVarslerJobb(
                merVeiledningVarselFinder,
                accessControlService,
                testEnv.urlEnv,
                merVeiledningVarselService
            )

        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation, contentNegotationFeature())
            application.routing {
                registerJobTriggerApi(sendMerVeiledningVarslerJobb, mikrofrontendService)
            }

            it("esyfovarsel-job trigger utsending av 2 varsler digitalt og 3 varsler som brev") {
                with(handleRequest(HttpMethod.Post, urlPathJobTrigger)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    coVerify(exactly = 5) { merVeiledningVarselService.sendVarselTilArbeidstaker(any(), any(), any()) }
                }
            }
        }
    }
})
