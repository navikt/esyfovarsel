package no.nav.syfo.consumer

import kotlinx.coroutines.runBlocking
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.testEnvironment
import no.nav.syfo.testutil.mocks.*
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

const val aktorIdNonReservedUser = aktorId
const val aktorIdReservedUser = aktorId2
const val aktorIdUnsuccessfulCall = aktorId3
const val aktorIdUnknownUser = aktorId4
const val aktorIdInvalid = "${aktorId}-with-invalid-input"

object DkifConsumerSpek : Spek({

    val testEnv = testEnvironment()
    val mockServers = MockServers(testEnv)
    val stsMockServer = mockServers.mockStsServer()
    val dkifMockServer = mockServers.mockDkifServer()

    val stsConsumer = StsConsumer(testEnv.commonEnv)
    val dkifConsumer = DkifConsumer(testEnv.commonEnv, stsConsumer)

    beforeGroup {
        stsMockServer.start()
        dkifMockServer.start()
    }

    afterGroup {
        stsMockServer.stop(1L, 10L)
        dkifMockServer.stop(1L, 10L)
    }

    describe("DkifConsumerSpek") {
        it("Call DKIF for non-reserved user") {
            val dkifResponse = runBlocking { dkifConsumer.kontaktinfo(aktorIdNonReservedUser) }
            dkifResponse shouldNotBe null
            dkifResponse!!.kanVarsles shouldEqual true
        }

        it("Call DKIF for reserved user") {
            val dkifResponse = runBlocking { dkifConsumer.kontaktinfo(aktorIdReservedUser) }
            dkifResponse shouldNotBe null
            dkifResponse!!.kanVarsles shouldEqual false
        }

        it("DKIF consumer should throw RuntimeException when call fails") {
            assertFailsWith(RuntimeException::class) {
                runBlocking { dkifConsumer.kontaktinfo(aktorIdUnsuccessfulCall) }
            }
        }

        it("DKIF consumer should throw RuntimeException when requesting data for unknown user") {
            assertFailsWith(RuntimeException::class) {
                runBlocking { dkifConsumer.kontaktinfo(aktorIdUnknownUser) }
            }
        }

        it("DKIF consumer should return null on invalid aktorid") {
            val dkifResponse = runBlocking { dkifConsumer.kontaktinfo(aktorIdInvalid) }
            dkifResponse shouldEqual null
        }
    }
})
