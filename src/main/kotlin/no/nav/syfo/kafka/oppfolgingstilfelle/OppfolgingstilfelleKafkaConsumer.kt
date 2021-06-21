package no.nav.syfo.kafka.oppfolgingstilfelle

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.kafka.KafkaListener
import no.nav.syfo.kafka.consumerProperties
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.KOppfolgingstilfellePeker
import no.nav.syfo.kafka.topicOppfolgingsTilfelle
import no.nav.syfo.service.AccessControl
import no.nav.syfo.varsel.VarselPlanner
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration

@KtorExperimentalAPI
class OppfolgingstilfelleKafkaConsumer(
    val env: Environment,
    val syfosyketilfelleConsumer: SyfosyketilfelleConsumer,
    val accessControl: AccessControl
) : KafkaListener {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.OppfolgingstilfelleConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val varselPlanners: ArrayList<VarselPlanner> = arrayListOf()
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    init {
        val kafkaConfig = consumerProperties(env)
        kafkaListener = KafkaConsumer<String, String>(kafkaConfig)
        kafkaListener.subscribe(listOf(topicOppfolgingsTilfelle))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Starting to listen to $topicOppfolgingsTilfelle")
        while (applicationState.running) {
            kafkaListener.poll(Duration.ofMillis(0)).forEach {
                log.info("Received record from [$topicOppfolgingsTilfelle]")
                try {
                    val peker: KOppfolgingstilfellePeker = objectMapper.readValue(it.value())
                    val aktorId = peker.aktorId
                    log.info("inside try")
                    val fnr = accessControl.getFnrIfUserCanBeNotified(aktorId)
                    log.info("Received fnr [$fnr]")
                    fnr?.let {
                        val oppfolgingstilfelle = syfosyketilfelleConsumer.getOppfolgingstilfelle(aktorId)
                        oppfolgingstilfelle?.let {
                            varselPlanners.forEach { planner -> runBlocking { planner.processOppfolgingstilfelle(oppfolgingstilfelle, fnr) } }
                        }
                    }
                } catch (e: IOException) {
                    log.error("Error in [$topicOppfolgingsTilfelle] listener: Could not parse message | ${e.message}")
                } catch (e: Exception) {
                    log.error("Error in [$topicOppfolgingsTilfelle] listener: Could not process message | ${e.message}")
                }
            }
            kafkaListener.commitSync()
            delay(10)
        }
    }

    fun addPlanner(varselPlanner: VarselPlanner): OppfolgingstilfelleKafkaConsumer {
        varselPlanners.add(varselPlanner)
        return this
    }
}
