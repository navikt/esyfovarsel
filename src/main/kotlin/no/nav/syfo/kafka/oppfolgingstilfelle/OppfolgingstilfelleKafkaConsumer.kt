package no.nav.syfo.kafka.oppfolgingstilfelle

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.delay
import no.nav.syfo.*
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.kafka.KafkaListener
import no.nav.syfo.varsel.VarselPlanner
import no.nav.syfo.kafka.consumerProperties
import no.nav.syfo.kafka.topicOppfolgingsTilfelle
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration

class OppfolgingstilfelleKafkaConsumer(env: Environment, syfosyketilfelleConsumer: SyfosyketilfelleConsumer) : KafkaListener {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.OppfolgingstilfelleConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val varselPlanners: ArrayList<VarselPlanner> = arrayListOf()
    private val syfosyketilfelleConsumer: SyfosyketilfelleConsumer
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    init {
        val kafkaConfig = consumerProperties(env)
        this.syfosyketilfelleConsumer = syfosyketilfelleConsumer
        kafkaListener = KafkaConsumer<String, String>(kafkaConfig)
        kafkaListener.subscribe(listOf(topicOppfolgingsTilfelle))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Starting to listen to $topicOppfolgingsTilfelle")
        while (applicationState.running) {
            kafkaListener.poll(Duration.ofMillis(0)).forEach {
                try {
                    val peker: KOppfolgingstilfellePeker = objectMapper.readValue(it.value())
                    val oppfolgingstilfelle = syfosyketilfelleConsumer.getOppfolgingstilfelle(peker.aktorId, peker.orgnummer)

                    oppfolgingstilfelle?.let {
                        varselPlanners.forEach { planner -> planner.planVarsel(it) }
                    }
                } catch (e: IOException) {
                    log.error("Error in [$topicOppfolgingsTilfelle] listener: Could not parse message | ${e.message}")
                } catch (e: Exception) {
                    log.error("Error in [$topicOppfolgingsTilfelle] listener: Could not process message | ${e.message}")
                }
            }
            delay(100)
        }
    }

    fun addPlanner(varselPlanner: VarselPlanner) : OppfolgingstilfelleKafkaConsumer {
        varselPlanners.add(varselPlanner)
        return this
    }

    data class KOppfolgingstilfellePeker(
        val aktorId: String,
        val orgnummer: String
    )
}
