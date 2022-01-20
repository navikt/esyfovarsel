package no.nav.syfo.kafka.oppfolgingstilfelle

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.KafkaListener
import no.nav.syfo.kafka.consumerProperties
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.KOppfolgingstilfellePeker
import no.nav.syfo.kafka.topicOppfolgingsTilfelle
import no.nav.syfo.metrics.tellFeilIParsing
import no.nav.syfo.metrics.tellFeilIPlanner
import no.nav.syfo.metrics.tellFeilIProsessering
import no.nav.syfo.service.AccessControl
import no.nav.syfo.varsel.VarselPlanner
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration

class OppfolgingstilfelleKafkaConsumer(
    val env: Environment,
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
        kafkaListener = KafkaConsumer(kafkaConfig)
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
                        println("Checkaccess BEGIN")
                        val fnr = accessControl.getFnrIfUserCanBeNotified(aktorId)
                        println("Checkaccess[FNR]: $fnr")
                        fnr?.let {
                            println("process: $fnr")
                            varselPlanners.forEach { planner ->
                                try {
                                    runBlocking { planner.processOppfolgingstilfelle(aktorId, fnr) }
                                } catch (e: Exception) {
                                    log.error("Error in [${planner.name}] planner: | ${e.message}", e)
                                    tellFeilIPlanner()
                                }
                            }
                        }
                        println("Checkaccess DONE")
                    } catch (e: IOException) {
                        log.error(
                            "Error in [$topicOppfolgingsTilfelle] listener: Could not parse message | ${e.message}",
                            e
                        )
                        tellFeilIParsing()
                    }
                }
                kafkaListener.commitSync()
                delay(10)
        }
        log.info("Stopped listening to $topicOppfolgingsTilfelle")
    }

    fun addPlanner(varselPlanner: VarselPlanner): OppfolgingstilfelleKafkaConsumer {
        varselPlanners.add(varselPlanner)
        return this
    }
}
