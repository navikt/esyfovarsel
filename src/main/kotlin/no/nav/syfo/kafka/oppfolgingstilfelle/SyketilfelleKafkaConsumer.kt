package no.nav.syfo.kafka.oppfolgingstilfelle

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.storeSyketilfellebit
import no.nav.syfo.db.toPSyketilfellebit
import no.nav.syfo.kafka.KafkaListener
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.KSyketilfellebit
import no.nav.syfo.kafka.syketilfelleConsumerProperties
import no.nav.syfo.kafka.topicFlexSyketilfellebiter
import no.nav.syfo.service.AccessControl
import no.nav.syfo.syketilfelle.or
import no.nav.syfo.varsel.VarselPlanner
import no.nav.syfo.varsel.VarselPlannerSyketilfelle
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration


class SyketilfelleKafkaConsumer(
    val env: Environment,
    val accessControl: AccessControl,
    val databaseInterface: DatabaseInterface
) : KafkaListener {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.kafka.SyketilfelleKafkaConsumer")
    private val kafkaListener: KafkaConsumer<String, String>
    private val zeroMillis = Duration.ofMillis(0L)
    private val varselPlanners: ArrayList<VarselPlannerSyketilfelle> = arrayListOf()
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

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
                    if (accessControl.canUserBeNotified(sykmeldtFnr) && kSyketilfellebit.orgnummer != null) {
                        varselPlanners.forEach {
                            it.processSyketilfelle(sykmeldtFnr, kSyketilfellebit.orgnummer)
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

    fun addPlanner(varselPlanner: VarselPlannerSyketilfelle): SyketilfelleKafkaConsumer {
        varselPlanners.add(varselPlanner)
        return this
    }
}
