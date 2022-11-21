package no.nav.syfo.kafka.consumers.syketilfelle

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.storeSyketilfellebit
import no.nav.syfo.db.toPSyketilfellebit
import no.nav.syfo.kafka.common.*
import no.nav.syfo.kafka.consumers.syketilfelle.domain.KSyketilfellebit
import no.nav.syfo.planner.VarselPlanner
import no.nav.syfo.service.AccessControlService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

class SyketilfelleKafkaConsumer(
    val env: Environment,
    val accessControlService: AccessControlService,
    val databaseInterface: DatabaseInterface
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.SyketilfelleKafkaConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val varselPlanners: ArrayList<VarselPlanner> = arrayListOf()
    private val objectMapper = createObjectMapper()

    init {
        val kafkaConfig = syketilfelleConsumerProperties(env)
        kafkaListener = KafkaConsumer(kafkaConfig)
        kafkaListener.subscribe(listOf(topicFlexSyketilfellebiter))
    }

    override suspend fun listen(applicationState: ApplicationState) {
        log.info("Started listening to topic $topicFlexSyketilfellebiter")
        while (applicationState.running) {
            kafkaListener.poll(zeroMillis).forEach {
                try {
                    val kSyketilfellebit: KSyketilfellebit = objectMapper.readValue(it.value())
                    databaseInterface.storeSyketilfellebit(kSyketilfellebit.toPSyketilfellebit())
                    val sykmeldtFnr = kSyketilfellebit.fnr
                    val userAccessStatus = accessControlService.getUserAccessStatus(sykmeldtFnr)

                    varselPlanners.forEach { planner ->
                        if (userAccessStatus.canUserBeDigitallyNotified) {
                            planner.processSyketilfelle(sykmeldtFnr, kSyketilfellebit.orgnummer)
                        } else {
                            log.info("Prosesserer ikke record fra $topicFlexSyketilfellebiter pga bruker med forespurt fnr er reservert og/eller gradert")
                        }
                    }
                } catch (e: IOException) {
                    log.error(
                        "Error in [$topicFlexSyketilfellebiter]-listener: Could not parse message | ${e.message}",
                        e
                    )
                } catch (e: Exception) {
                    log.error(
                        "Exception in [$topicFlexSyketilfellebiter]-listener: ${e.message}",
                        e
                    )
                }
                kafkaListener.commitSync()
            }
        }
    }

    fun addPlanner(varselPlanner: VarselPlanner): SyketilfelleKafkaConsumer {
        varselPlanners.add(varselPlanner)
        return this
    }
}