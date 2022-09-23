package no.nav.syfo.consumer

import kotlinx.coroutines.runBlocking
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.getTestEnv
import no.nav.syfo.testutil.mocks.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

const val fnrNonReservedUser = fnr1
const val fnrReservedUser = fnr2
const val fnrInvalid = "${fnr1}-with-invalid-input"

object DkifConsumerSpek : Spek({

    val testEnv = getTestEnv()
    val mockServers = MockServers(testEnv.urlEnv, testEnv.authEnv)
    val azureAdMockServer = mockServers.mockAADServer()
    val dkifMockServer = mockServers.mockDkifServer()

    val azureAdConsumer = AzureAdTokenConsumer(testEnv.authEnv)
    val dkifConsumer = DkifConsumer(testEnv.urlEnv, azureAdConsumer)

    beforeGroup {
        azureAdMockServer.start()
        dkifMockServer.start()
    }

    afterGroup {
        azureAdMockServer.stop(1L, 10L)
        dkifMockServer.stop(1L, 10L)
    }

    describe("DkifConsumerSpek") {
        it("Call DKIF for non-reserved user") {
            val dkifResponse = runBlocking { dkifConsumer.person(fnrNonReservedUser) }
            dkifResponse shouldNotBe null
            dkifResponse!!.kanVarsles shouldBeEqualTo true
        }

        it("Call DKIF for reserved user") {
            val dkifResponse = runBlocking { dkifConsumer.person(fnrReservedUser) }
            dkifResponse shouldNotBe null
            dkifResponse!!.kanVarsles shouldBeEqualTo false
        }

        it("DKIF consumer should return null on invalid aktorid") {
            val dkifResponse = runBlocking { dkifConsumer.person(fnrInvalid) }
            dkifResponse shouldBeEqualTo null
        }
    }
})
