package no.nav.syfo.kafka

import kotlinx.coroutines.runBlocking
import no.nav.common.KafkaEnvironment
import no.nav.syfo.ApplicationState
import no.nav.syfo.auth.StsConsumer
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.kafka.oppfolgingstilfelle.OppfolgingstilfelleKafkaConsumer
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.KOppfolgingstilfellePeker
import no.nav.syfo.testEnviornment
import no.nav.syfo.testutil.kafka.JacksonKafkaSerializer
import no.nav.syfo.testutil.mocks.MockVarselPlaner
import no.nav.syfo.testutil.mocks.StsMockServer
import no.nav.syfo.testutil.mocks.SyfosyketilfelleMockServer
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

    val testEnv = testEnviornment(embeddedKafkaEnv.brokersURL)
    val recordKey = "dummykey"
    val fakeProducerRecord = ProducerRecord(topicOppfolgingsTilfelle, recordKey, kafkaOppfolgingstilfellePeker)
    val producerProperties = consumerProperties(testEnv).apply {
        put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("value.serializer", JacksonKafkaSerializer::class.java)
    }

    val fakeOppfolgingstilfelleKafkaProducer = KafkaProducer<String, KOppfolgingstilfellePeker>(producerProperties)

    val mockSyfosyketilfelleServer = SyfosyketilfelleMockServer(testEnv).mockServer()
    val stsServer = StsMockServer(testEnv).mockServer()

    val stsConsumer = StsConsumer(testEnv)
    val syfosyketilfelleConsumer = SyfosyketilfelleConsumer(testEnv, stsConsumer)
    val oppfolgingstilfelleKafkaConsumer = OppfolgingstilfelleKafkaConsumer(testEnv, syfosyketilfelleConsumer)
        .addPlanner(MockVarselPlaner(fakeApplicationState))


    beforeGroup {
        embeddedKafkaEnv.start()
        mockSyfosyketilfelleServer.start()
        stsServer.start()
    }

    afterGroup {
        embeddedKafkaEnv.tearDown()
        mockSyfosyketilfelleServer.stop(1L, 10L)
        stsServer.stop(1L, 10L)
    }

    describe("Test Kafka consumer with face producer") {
        it("Consume record from $topicOppfolgingsTilfelle") {
            fakeOppfolgingstilfelleKafkaProducer.send(fakeProducerRecord)
            runBlocking { oppfolgingstilfelleKafkaConsumer.listen(fakeApplicationState) }
        }
    }

})
