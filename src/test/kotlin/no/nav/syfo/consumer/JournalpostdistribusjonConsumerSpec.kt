package no.nav.syfo.consumer

import com.benasher44.uuid.Uuid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType
import no.nav.syfo.consumer.distribuerjournalpost.JournalpostdistribusjonConsumer
import no.nav.syfo.exceptions.JournalpostDistribusjonGoneException
import no.nav.syfo.getTestEnv
import no.nav.syfo.testutil.mocks.MockServers
import org.amshove.kluent.shouldBeEqualTo

class JournalpostdistribusjonConsumerSpec :
    DescribeSpec({
        val testEnv = getTestEnv()
        val mockServers = MockServers(testEnv.urlEnv, testEnv.authEnv)
        val azureAdMockServer = mockServers.mockAADServer()
        val journalpostDistribusionServer = mockServers.mockJournalpostdistribusjonServer()

        val azureAdConsumer = AzureAdTokenConsumer(testEnv.authEnv)
        val consumer = JournalpostdistribusjonConsumer(testEnv.urlEnv, azureAdConsumer)

        beforeSpec {
            azureAdMockServer.start()
            journalpostDistribusionServer.start()
        }

        afterSpec {
            azureAdMockServer.stop(1L, 10L)
            journalpostDistribusionServer.stop(1L, 10L)
        }

        describe("JournalpostdistribusjonConsumerSpec") {
            it("Response with bestillingsId for OK journalpost") {
                val response =
                    consumer.distribuerJournalpost("100", Uuid.randomUUID().toString(), DistibusjonsType.ANNET, true)
                response.bestillingsId shouldBeEqualTo "1000"
            }

            it("Response with bestillingsId for conflict on journalpost") {
                val response =
                    consumer.distribuerJournalpost("CONFLICT", Uuid.randomUUID().toString(), DistibusjonsType.ANNET, true)
                response.bestillingsId shouldBeEqualTo "1000"
            }

            it("Throw exception when distribuerJournalpost respondes with 410 status") {
                shouldThrow<JournalpostDistribusjonGoneException> {
                    consumer.distribuerJournalpost("GONE", Uuid.randomUUID().toString(), DistibusjonsType.ANNET, true)
                }
            }
        }
    })
