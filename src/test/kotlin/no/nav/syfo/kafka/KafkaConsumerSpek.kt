package no.nav.syfo.kafka

import kotlinx.coroutines.runBlocking
import no.nav.common.KafkaEnvironment
import no.nav.syfo.ApplicationState
import no.nav.syfo.auth.AzureAdTokenConsumer
import no.nav.syfo.consumer.dkif.DkifConsumer
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.getTestEnv
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.KOppfolgingstilfellePeker
import no.nav.syfo.service.AccessControl
import no.nav.syfo.testutil.kafka.JacksonKafkaSerializer
import no.nav.syfo.testutil.mocks.MockServers
import no.nav.syfo.testutil.mocks.MockVarselPlaner
import no.nav.syfo.testutil.mocks.kafkaOppfolgingstilfellePeker
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KafkaConsumerSpek : Spek({

    defaultTimeout = 20000L

    val embeddedKafkaEnv = KafkaEnvironment(
        topicNames = listOf(topicOppfolgingsTilfelle)
    )

    val fakeApplicationState = ApplicationState(running = true, initialized = true)

    val testEnv = getTestEnv(embeddedKafkaEnv.brokersURL)
    val recordKey = "dummykey"
    val fakeProducerRecord = ProducerRecord(topicOppfolgingsTilfelle, recordKey, kafkaOppfolgingstilfellePeker)
    val producerProperties = consumerProperties(testEnv).apply {
        put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("value.serializer", JacksonKafkaSerializer::class.java)
    }

    val fakeOppfolgingstilfelleKafkaProducer = KafkaProducer<String, KOppfolgingstilfellePeker>(producerProperties)

    val mockServers = MockServers(testEnv.urlEnv, testEnv.authEnv)

    val azureADServer = mockServers.mockAADServer()
    val dkifServer = mockServers.mockDkifServer()
    val pdlServer = mockServers.mockPdlServer()
    val syfosyketilfelleServer = mockServers.mockSyfosyketilfelleServer()

    val azureAdTokenConsumer = AzureAdTokenConsumer(testEnv.authEnv)
    val pdlConsumer = PdlConsumer(testEnv.urlEnv, azureAdTokenConsumer)
    val dkifConsumer = DkifConsumer(testEnv.urlEnv, azureAdTokenConsumer)
    val accessControl = AccessControl(pdlConsumer, dkifConsumer)
    val oppfolgingstilfelleKafkaConsumer = OppfolgingstilfelleKafkaConsumer(testEnv, accessControl)
        .addPlanner(MockVarselPlaner(fakeApplicationState))

    beforeGroup {
        embeddedKafkaEnv.start()
        azureADServer.start()
        dkifServer.start()
        pdlServer.start()
        syfosyketilfelleServer.start()
    }

    afterGroup {
        embeddedKafkaEnv.tearDown()
        azureADServer.stop(1L, 10L)
        dkifServer.stop(1L, 10L)
        pdlServer.stop(1L, 10L)
        syfosyketilfelleServer.stop(1L, 10L)
    }

    describe("Test Kafka consumer with fake producer") {
        it("Consume record from $topicOppfolgingsTilfelle") {
            fakeOppfolgingstilfelleKafkaProducer.send(fakeProducerRecord)
            runBlocking { oppfolgingstilfelleKafkaConsumer.listen(fakeApplicationState) }
        }
    }


})
