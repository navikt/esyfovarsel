package no.nav.syfo.service

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchFodselsdatoByFnr
import no.nav.syfo.db.fetchMerVeiledningVarslerToSend
import no.nav.syfo.utils.*
import org.slf4j.LoggerFactory

class MerVeiledningVarselFinder(
    private val databaseAccess: DatabaseInterface,
    private val sykmeldingService: SykmeldingService,
    private val pdlConsumer: PdlConsumer,
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.MerVeiledningVarselFinder")

    suspend fun findMerVeiledningVarslerToSendToday(): List<PPlanlagtVarsel> {
        log.info("[MerVeiledningVarselFinder] Henter kandidater for Mer veiledning-varsler")
        val alleMerVeiledningVarsler = databaseAccess.fetchMerVeiledningVarslerToSend() //UTB

        log.info("[MerVeiledningVarselFinder] Slår opp sykmeldinger")
        val merVeiledningVarslerSomHarSykmelding = alleMerVeiledningVarsler
            .filter { sykmeldingService.isPersonSykmeldtPaDato(LocalDate.now(), it.fnr) }

        log.info("[MerVeiledningVarselFinder] sjekker fodselsdato")
        val merVeiledningVarslerSomSkalSendesIDag = merVeiledningVarslerSomHarSykmelding
            .filter { isBrukerUnder67Ar(it.fnr) }

        log.info("[MerVeiledningVarselFinder] Antall MER_VEILEDNING varsler fra Spleis/Infotrygd: ${merVeiledningVarslerSomSkalSendesIDag.size}")

        return merVeiledningVarslerSomSkalSendesIDag.map {
            PPlanlagtVarsel(
                uuid = it.id.toString(),
                fnr = it.fnr,
                orgnummer = null,
                aktorId = null,
                type = VarselType.MER_VEILEDNING.name,
                utsendingsdato = LocalDate.now(),
                sistEndret = LocalDateTime.now(),
                opprettet = LocalDateTime.now(),
            )
        }
    }

    private fun isBrukerUnder67Ar(fnr: String): Boolean {
        val storedBirthdate = databaseAccess.fetchFodselsdatoByFnr(fnr)
        return if (storedBirthdate.isEmpty() || storedBirthdate.first().isNullOrEmpty()) {
            log.info("[MerVeiledningVarselFinder] Mangler lagret fodselsdato, sjekker PDL pa nytt")
            pdlConsumer.isBrukerYngreEnn67(fnr)
        } else {
            log.info("[MerVeiledningVarselFinder] Sjekker om person er under 67 ut fra lagret fodselsdato")
            isFodselsdatoMindreEnn67Ar(storedBirthdate.first())
        }
    }
}
