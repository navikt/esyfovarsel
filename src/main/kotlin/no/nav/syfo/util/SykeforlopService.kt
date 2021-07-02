package no.nav.syfo.util

import no.nav.syfo.consumer.domain.Sykeforlop
import no.nav.syfo.consumer.domain.Syketilfelledag
import no.nav.syfo.consumer.domain.Sykmeldingtilfelle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit

class SykeforlopService {
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.varsel.SykeforlopService")
    private val SYKEFORLOP_MIN_DIFF_DAGER: Long = 16

    private fun getsisteSykmeldingtilfellerForRessursId(syketilfelledager: List<Syketilfelledag>): List<Sykmeldingtilfelle> {
        val sykmeldingtilfeller: MutableList<Sykmeldingtilfelle> = mutableListOf()
        val ressursIds: Set<String?> = syketilfelledager.map { i -> i.prioritertSyketilfellebit?.ressursId }.toSet()

        ressursIds.forEach { it ->
            val ressursId = it
            val biterMedSammeSykmeldingId = syketilfelledager.filter { it.prioritertSyketilfellebit?.ressursId == ressursId }
                .map { i -> i.prioritertSyketilfellebit }

            log.info("[AKTIVITETSKRAV_VARSEL]: biterMedSammeSykmeldingId, id:  $biterMedSammeSykmeldingId, $ressursId")//Todo: delete

            val sisteBit = biterMedSammeSykmeldingId.sortedByDescending { it?.opprettet }[0]
            log.info("[AKTIVITETSKRAV_VARSEL]: sisteBit, id: $sisteBit")//Todo: delete

            if (sisteBit != null) {
                sykmeldingtilfeller.add(Sykmeldingtilfelle(ressursId!!, sisteBit.fom.toLocalDate(), sisteBit.tom.toLocalDate(), sisteBit.opprettet))
            }
        }
        log.info("[AKTIVITETSKRAV_VARSEL]: Laget sykmeldingtilfeller:  $sykmeldingtilfeller")//Todo: delete
        return sykmeldingtilfeller
    }

    fun getSykeforlopList(syketilfelledager: List<Syketilfelledag>): List<Sykeforlop> {
        if (syketilfelledager.isNotEmpty()) {
            val sorterteSisteSykmeldingtilfellerForRessursId = getsisteSykmeldingtilfellerForRessursId(syketilfelledager).sortedBy { it.opprettet }//Nyeste sist
            val sykeforloper: MutableList<Sykeforlop> = mutableListOf()

            var forrigeTilfelle: Sykmeldingtilfelle = sorterteSisteSykmeldingtilfellerForRessursId[0]
            var sykeforlop = Sykeforlop(mutableSetOf(forrigeTilfelle.ressursId), forrigeTilfelle.fom, forrigeTilfelle.tom)

            for (i in 1..sorterteSisteSykmeldingtilfellerForRessursId.size - 1) {
                val navarendeTilfelle: Sykmeldingtilfelle = sorterteSisteSykmeldingtilfellerForRessursId[i]

                if (ChronoUnit.DAYS.between(forrigeTilfelle.tom, navarendeTilfelle.fom) > SYKEFORLOP_MIN_DIFF_DAGER) {
                    sykeforloper.add(sykeforlop)
                    sykeforlop = Sykeforlop(mutableSetOf(navarendeTilfelle.ressursId), navarendeTilfelle.fom, navarendeTilfelle.tom)
                } else if (ChronoUnit.DAYS.between(forrigeTilfelle.tom, navarendeTilfelle.fom) <= SYKEFORLOP_MIN_DIFF_DAGER) {
                    if (navarendeTilfelle.fom.isBefore(forrigeTilfelle.tom) && navarendeTilfelle.opprettet.isAfter(forrigeTilfelle.opprettet)) {
                        sykeforlop.fom = navarendeTilfelle.fom
                    }
                    sykeforlop.ressursIds.add(navarendeTilfelle.ressursId)
                    sykeforlop.tom = navarendeTilfelle.tom
                }
                forrigeTilfelle = navarendeTilfelle
            }
            sykeforloper.add(sykeforlop)
            log.info("[AKTIVITETSKRAV_VARSEL]: Laget sykeforloper:  $sykeforloper")//TODO: delete
            return sykeforloper
        }
        return listOf()
    }
}