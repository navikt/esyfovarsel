package no.nav.syfo.service

import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchFodselsdatoByFnr
import no.nav.syfo.db.fetchMerVeiledningVarslerToSend
import no.nav.syfo.utils.isAlderMindreEnnGittAr
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class MerVeiledningVarselFinder(
    private val databaseAccess: DatabaseInterface,
    private val sykmeldingService: SykmeldingService,
    private val pdlConsumer: PdlConsumer,
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.MerVeiledningVarselFinder")

    suspend fun findMerVeiledningVarslerToSendToday(): List<PPlanlagtVarsel> {
        log.info("[MerVeiledningVarselFinder] Henter kandidater for Mer veiledning-varsler")
        val alleMerVeiledningVarsler = databaseAccess.fetchMerVeiledningVarslerToSend()

        log.info("[MerVeiledningVarselFinder] Slår opp sykmeldinger")
        val merVeiledningVarslerSomHarSykmelding = alleMerVeiledningVarsler
            .filter { sykmeldingService.isPersonSykmeldtPaDato(LocalDate.now(), it.fnr) }

        log.info("[MerVeiledningVarselFinder] sjekker fodselsdato")
        val merVeiledningVarslerSomSkalSendesIDag = merVeiledningVarslerSomHarSykmelding
            .filter { isBrukerYngreEnn67Ar(it.fnr) }

        log.info("[MerVeiledningVarselFinder] Antall MER_VEILEDNING varsler fra Spleis/Infotrygd: ${merVeiledningVarslerSomSkalSendesIDag.size}")

        // Todo: delete after test
        return listOf(
            PPlanlagtVarsel(
                "ee3f5b44-b6e3-4220-9afc-f8fc1f627c85",
                fnr = "26918198953",
                orgnummer = null,
                aktorId = null,
                VarselType.MER_VEILEDNING.name,
                LocalDate.now(),
                sistEndret = LocalDateTime.now(),
                opprettet = LocalDateTime.now(),
            ),
        )

//        return merVeiledningVarslerSomSkalSendesIDag.map {
//            PPlanlagtVarsel(
//                uuid = it.id.toString(),
//                fnr = it.fnr,
//                orgnummer = null,
//                aktorId = null,
//                type = VarselType.MER_VEILEDNING.name,
//                utsendingsdato = LocalDate.now(),
//                sistEndret = LocalDateTime.now(),
//                opprettet = LocalDateTime.now(),
//            )
//        }
    }

    suspend fun isBrukerYngreEnn67Ar(fnr: String): Boolean {
        val storedBirthdateList = databaseAccess.fetchFodselsdatoByFnr(fnr)
        val storedBirthdate = if (storedBirthdateList.isNotEmpty()) storedBirthdateList.first() else null

        return if (storedBirthdate.isNullOrEmpty()) {
            log.info("[MerVeiledningVarselFinder] Mangler lagret fodselsdato, sjekker PDL pa nytt")
            pdlConsumer.isBrukerYngreEnnGittMaxAlder(fnr, 67)
        } else {
            log.info("[MerVeiledningVarselFinder] Sjekker om person er under 67 ut fra lagret fodselsdato")
            return isAlderMindreEnnGittAr(storedBirthdate, 67)
        }
    }
}
