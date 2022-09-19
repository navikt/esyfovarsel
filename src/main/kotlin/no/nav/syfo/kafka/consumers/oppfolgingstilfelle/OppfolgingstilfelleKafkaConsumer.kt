package no.nav.syfo.kafka.consumers.oppfolgingstilfelle

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.common.KafkaListener
import no.nav.syfo.kafka.common.consumerProperties
import no.nav.syfo.kafka.common.createObjectMapper
import no.nav.syfo.kafka.common.topicOppfolgingsTilfelle
import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.KOppfolgingstilfellePeker
import no.nav.syfo.metrics.tellFeilIParsing
import no.nav.syfo.metrics.tellFeilIPlanner
import no.nav.syfo.planner.VarselPlannerOppfolgingstilfelle
import no.nav.syfo.service.AccessControlService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration

class OppfolgingstilfelleKafkaConsumer(
    val env: Environment,
    val accessControlService: AccessControlService
) : KafkaListener {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.OppfolgingstilfelleConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val varselPlannerOppfolgingstilfelles: ArrayList<VarselPlannerOppfolgingstilfelle> = arrayListOf()
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = consumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicOppfolgingsTilfelle))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $topicOppfolgingsTilfelle")
        while (applicationState.running) {
            kafkaListener.poll(Duration.ofMillis(0)).forEach {
                log.info("Received record from topic: [$topicOppfolgingsTilfelle]")
                try {
                    val peker: KOppfolgingstilfellePeker = objectMapper.readValue(it.value())
                    val aktorId = peker.aktorId
                    val orgnummer = peker.orgnummer
                    val userAccessStatus = accessControlService.getUserAccessStatusByAktorId(aktorId)

                    varselPlannerOppfolgingstilfelles.forEach { planner ->
                        if (planner.varselSkalLagres(userAccessStatus)) {
                            try {
                                runBlocking {
                                    userAccessStatus.fnr?.let { fnr -> planner.processOppfolgingstilfelle(aktorId, fnr, orgnummer) }
                                        ?: log.info("Klarte ikke hente fnr fra PDL")
                                }
                            } catch (e: Exception) {
                                log.error("Error in [${planner.name}] planner: | ${e.message}", e)
                                tellFeilIPlanner()
                            }
                        } else {
                            log.info("Prosesserer ikke record fra $topicOppfolgingsTilfelle pga bruker med forespurt fnr er reservert og/eller gradert")
                        }
                    }
                } catch (e: IOException) {
                    log.error(
                        "Error in [$topicOppfolgingsTilfelle] listener: Could not parse message | ${e.message}",
                        e
                    )
                    tellFeilIParsing()
                }
                kafkaListener.commitSync()
                delay(10)
            }
        }
        log.info("Stopped listening to $topicOppfolgingsTilfelle")
    }

    fun addPlanner(varselPlannerOppfolgingstilfelle: VarselPlannerOppfolgingstilfelle): OppfolgingstilfelleKafkaConsumer {
        varselPlannerOppfolgingstilfelles.add(varselPlannerOppfolgingstilfelle)
        return this
    }
}
