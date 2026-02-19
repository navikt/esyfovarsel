package no.nav.syfo.consumer

import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.getTestEnv
import no.nav.syfo.testutil.mocks.FNR_1
import no.nav.syfo.testutil.mocks.FNR_2
import no.nav.syfo.testutil.mocks.MockServers
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe

const val FNR_NON_RESERVED_USER = FNR_1
const val FNR_RESERVERD_USER = FNR_2

class DkifConsumerSpek :
    DescribeSpec({

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
                val dkifResponse = dkifConsumer.person(FNR_NON_RESERVED_USER)
                dkifResponse shouldNotBe null
                dkifResponse!!.kanVarsles shouldBeEqualTo true
            }

            it("Call DKIF for reserved user") {
                val dkifResponse = dkifConsumer.person(FNR_RESERVERD_USER)
                dkifResponse shouldNotBe null
                dkifResponse!!.kanVarsles shouldBeEqualTo false
            }

            it("DKIF consumer should return null on invalid aktorid") {
                val dkifResponse = dkifConsumer.person("serverdown")
                dkifResponse shouldBeEqualTo null
            }
        }
    })
