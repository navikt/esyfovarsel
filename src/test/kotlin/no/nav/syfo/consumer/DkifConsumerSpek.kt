package no.nav.syfo.consumer

import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.getTestEnv
import no.nav.syfo.testutil.mocks.MockServers
import no.nav.syfo.testutil.mocks.fnr1
import no.nav.syfo.testutil.mocks.fnr2
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe

const val fnrNonReservedUser = fnr1
const val fnrReservedUser = fnr2

class DkifConsumerSpek : DescribeSpec({

    val testEnv = getTestEnv()
    val mockServers = MockServers(testEnv.urlEnv, testEnv.authEnv)
    val azureAdMockServer = mockServers.mockAADServer()
    val dkifMockServer = mockServers.mockDkifServer()

    val azureAdConsumer = AzureAdTokenConsumer(testEnv.authEnv)
    val dkifConsumer = DkifConsumer(testEnv.urlEnv, azureAdConsumer)

    beforeSpec {
        azureAdMockServer.start()
        dkifMockServer.start()
    }

    afterSpec {
        azureAdMockServer.stop(1L, 10L)
        dkifMockServer.stop(1L, 10L)
    }

    describe("DkifConsumerSpek") {
        it("Call DKIF for non-reserved user") {
            val dkifResponse = dkifConsumer.person(fnrNonReservedUser)
            dkifResponse shouldNotBe null
            dkifResponse!!.kanVarsles shouldBeEqualTo true
        }

        it("Call DKIF for reserved user") {
            val dkifResponse = dkifConsumer.person(fnrReservedUser)
            dkifResponse shouldNotBe null
            dkifResponse!!.kanVarsles shouldBeEqualTo false
        }

        it("DKIF consumer should return null on invalid aktorid") {
            val dkifResponse = dkifConsumer.person("serverdown")
            dkifResponse shouldBeEqualTo null
        }
    }
})
