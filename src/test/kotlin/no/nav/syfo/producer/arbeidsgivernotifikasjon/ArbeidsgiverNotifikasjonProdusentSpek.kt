package no.nav.syfo.producer.arbeidsgivernotifikasjon

import io.kotest.core.spec.style.DescribeSpec
import kotlinx.coroutines.runBlocking
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.getTestEnv
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.testutil.mocks.MockServers
import no.nav.syfo.testutil.mocks.fnr1
import no.nav.syfo.testutil.mocks.fnr2
import no.nav.syfo.testutil.mocks.orgnummer
import java.time.LocalDateTime
import java.util.*

class ArbeidsgiverNotifikasjonProdusentSpek : DescribeSpec({
    val testEnv = getTestEnv()
    val mockServers = MockServers(testEnv.urlEnv, testEnv.authEnv)
    val azureAdMockServer = mockServers.mockAADServer()
    val azureAdConsumer = AzureAdTokenConsumer(testEnv.authEnv)
    val arbeidsgiverNotifikasjonMockServer = mockServers.mockArbeidsgiverNotifikasjonServer()
    val arbeidsgiverNotifikasjonProdusent = ArbeidsgiverNotifikasjonProdusent(testEnv.urlEnv, azureAdConsumer)

    val arbeidsgiverNotifikasjon = ArbeidsgiverNotifikasjon(
        UUID.randomUUID().toString(),
        orgnummer,
        "",
        fnr1,
        fnr2,
        "hei",
        "test@test.no",
        "Oppfølging",
        "Hei du",
        "Body",
        LocalDateTime.now().plusDays(1),
    )

    beforeSpec {
        azureAdMockServer.start()
        arbeidsgiverNotifikasjonMockServer.start()
    }

    afterSpec {
        azureAdMockServer.stop(1L, 10L)
        arbeidsgiverNotifikasjonMockServer.stop(1L, 10L)
    }

    describe("ArbeidsgiverNotifikasjonProdusentSpek") {
        it("Should send oppgave") {
            runBlocking { arbeidsgiverNotifikasjonProdusent.createNewTaskForArbeidsgiver(arbeidsgiverNotifikasjon) }
        }

        it("Should send beskjed") {
            runBlocking {
                arbeidsgiverNotifikasjonProdusent.createNewNotificationForArbeidsgiver(
                    arbeidsgiverNotifikasjon
                )
            }
        }
    }
})
