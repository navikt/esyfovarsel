package no.nav.syfo.kafka

import kotlinx.coroutines.runBlocking
import no.nav.common.KafkaEnvironment
import no.nav.syfo.ApplicationState
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.DkifConsumer
import no.nav.syfo.consumer.PdlConsumer
import no.nav.syfo.getTestEnv
import no.nav.syfo.kafka.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.KOppfolgingstilfellePeker
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

    val mockServers = MockServers(testEnv.urlEnv)

    val stsServer = mockServers.mockStsServer()
    val dkifServer = mockServers.mockDkifServer()
    val pdlServer = mockServers.mockPdlServer()
    val syfosyketilfelleServer = mockServers.mockSyfosyketilfelleServer()

    val stsConsumer = StsConsumer(testEnv.urlEnv, testEnv.authEnv)
    val pdlConsumer = PdlConsumer(testEnv.urlEnv, stsConsumer)
    val dkifConsumer = DkifConsumer(testEnv.urlEnv, stsConsumer)
    val accessControl = AccessControl(pdlConsumer, dkifConsumer)
    val oppfolgingstilfelleKafkaConsumer = OppfolgingstilfelleKafkaConsumer(testEnv, accessControl)
        .addPlanner(MockVarselPlaner(fakeApplicationState))


    beforeGroup {
        embeddedKafkaEnv.start()
        stsServer.start()
        dkifServer.start()
        pdlServer.start()
        syfosyketilfelleServer.start()
    }

    afterGroup {
        embeddedKafkaEnv.tearDown()
        stsServer.stop(1L, 10L)
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
