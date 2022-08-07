package no.nav.syfo.service.migration

import no.nav.syfo.Environment
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.fetchPlanlagtVarselWithUtsendingAfterDate
import no.nav.syfo.db.isVarselAlreadyPlanned
import no.nav.syfo.db.storeMigratredPlanlagtVarsel
import no.nav.syfo.kafka.producers.migration.VarselMigrationKafkaProducer
import no.nav.syfo.kafka.producers.migration.domain.FSSVarsel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

class MigrationService(
    env: Environment,
    val database: DatabaseInterface,
    val varselMigrationKafkaProducer: VarselMigrationKafkaProducer
) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.migration.MigrationService")

    fun migrateUnsentVarsler(): Pair<Int, Int> {
        val today = LocalDate.now()
        val varslerAfterToday = database.fetchPlanlagtVarselWithUtsendingAfterDate(today)
        var nrVarslerMigrated = 0
        varslerAfterToday.forEach {
            val fssVarsel = FSSVarsel(
                it.uuid,
                it.fnr,
                it.aktorId,
                it.type,
                it.utsendingsdato,
                it.opprettet,
                it.sistEndret,
                it.orgnummer
            )
            if (varselMigrationKafkaProducer.migrateVarsel(fssVarsel)) {
                nrVarslerMigrated++
            }
        }
        return Pair(nrVarslerMigrated, varslerAfterToday.size)
    }

    fun receiveUnsentVarsel(fssVarsel: FSSVarsel) {
        val fssUUID = fssVarsel.uuid
        if (database.isVarselAlreadyPlanned(fssVarsel)) {
            log.info("Notification with UUID $fssUUID is already planned. Skip migration")
            return
        }
        database.storeMigratredPlanlagtVarsel(fssVarsel)
        log.info("Migrated notification with UUID $fssUUID")
    }
}
