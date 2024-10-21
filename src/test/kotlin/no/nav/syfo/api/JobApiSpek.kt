package no.nav.syfo.api

import io.kotest.core.spec.style.DescribeSpec
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import no.nav.syfo.api.job.registerJobTriggerApi
import no.nav.syfo.api.job.urlPathJobTrigger
import no.nav.syfo.service.microfrontend.MikrofrontendService
import no.nav.syfo.util.contentNegotationFeature
import org.amshove.kluent.shouldBeEqualTo

class JobApiSpek : DescribeSpec({

    timeout = 20000L

    describe("JobTriggerApi test") {
        val mikrofrontendService = mockk<MikrofrontendService>(relaxed = true)

        justRun { mikrofrontendService.findAndCloseExpiredMikrofrontends() }

        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation, contentNegotationFeature())
            application.routing {
                registerJobTriggerApi(mikrofrontendService)
            }

            it("esyfovarsel-job does not trigger sending of varsler") {
                with(handleRequest(HttpMethod.Post, urlPathJobTrigger)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    coVerify(exactly = 0) { mikrofrontendService.updateMikrofrontendForUserByHendelse(any()) }
                }
            }
        }
    }
})
