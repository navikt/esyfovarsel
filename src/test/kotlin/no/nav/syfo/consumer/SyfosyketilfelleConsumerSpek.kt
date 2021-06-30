package no.nav.syfo.consumer

import kotlinx.coroutines.runBlocking
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.testEnviornment
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import no.nav.syfo.testutil.mocks.*
import org.amshove.kluent.shouldNotBe
import org.amshove.kluent.shouldNotBeEmpty

const val aktorId = no.nav.syfo.testutil.mocks.aktorId

object SyfosyketilfelleConsumerSpek : Spek({

    val testEnv = testEnviornment()
    val mockServers = MockServers(testEnv)
    val stsMockServer = mockServers.mockStsServer()
    val syfosyketilfelleMockServer = mockServers.mockSyfosyketilfelleServer()

    val stsConsumer = StsConsumer(testEnv)
    val syfosyketilfelleConsumer = SyfosyketilfelleConsumer(testEnv, stsConsumer)

    beforeGroup {
        stsMockServer.start()
        syfosyketilfelleMockServer.start()
    }

    afterGroup {
        stsMockServer.stop(1L, 10L)
        syfosyketilfelleMockServer.stop(1L, 10L)
    }

    describe("SyfosyketilfelleConsumerSpek") {
        it("Call syfosyketilfelle") {
            val sykeforlop = runBlocking { syfosyketilfelleConsumer.getSykeforlop(aktorId) }
            sykeforlop shouldNotBe null
            sykeforlop.shouldNotBeEmpty()
        }
    }
})
