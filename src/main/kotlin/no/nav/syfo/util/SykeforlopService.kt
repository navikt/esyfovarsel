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

    private fun groupByRessursId(syketilfelledager: List<Syketilfelledag>): List<Sykmeldingtilfelle> {
        val sykmeldingtilfeller: MutableList<Sykmeldingtilfelle> = mutableListOf()
        val ressursIds: Set<String?> = syketilfelledager.map { i -> i.prioritertSyketilfellebit?.ressursId }.toSet()

        ressursIds.forEach { it ->
            val id = it
            val biterMedSammeSykmeldingId = syketilfelledager.filter { it.prioritertSyketilfellebit?.ressursId == id }
                .map { i -> i.prioritertSyketilfellebit }

            val sisteBit = biterMedSammeSykmeldingId.sortedByDescending { it?.opprettet }[0]

            if (sisteBit != null) {
                sykmeldingtilfeller.add(Sykmeldingtilfelle(id!!, sisteBit.fom.toLocalDate(), sisteBit.tom.toLocalDate()))
            }
        }
        log.info("[AKTIVITETSKRAV_VARSEL]: Laget sykmeldingtilfeller:  $sykmeldingtilfeller")
        return sykmeldingtilfeller
    }

    fun getSykeforloper(syketilfelledager: List<Syketilfelledag>): List<Sykeforlop> {
        if (syketilfelledager.isNotEmpty()) {
            val grupperteSyketilfelledager = groupByRessursId(syketilfelledager)
            val sykeforloper: MutableList<Sykeforlop> = mutableListOf()

            var prevTilf: Sykmeldingtilfelle = grupperteSyketilfelledager[0]
            var sykeforlop = Sykeforlop(mutableListOf(prevTilf.ressursId), prevTilf.fom, prevTilf.tom)

            for (i in 1..grupperteSyketilfelledager.size - 1) {
                val currTilf: Sykmeldingtilfelle = grupperteSyketilfelledager[i]
                if (ChronoUnit.DAYS.between(prevTilf.tom, currTilf.fom) <= SYKEFORLOP_MIN_DIFF_DAGER) {
                    sykeforlop.ressursIds.add(currTilf.ressursId)
                    sykeforlop.tom = currTilf.tom
                } else {
                    sykeforloper.add(sykeforlop)
                    sykeforlop = Sykeforlop(mutableListOf(currTilf.ressursId), currTilf.fom, currTilf.tom)
                }
                prevTilf = currTilf
            }
            sykeforloper.add(sykeforlop)
            log.info("[AKTIVITETSKRAV_VARSEL]: Laget sykeforloper:  $sykeforloper")
            return sykeforloper
        }
        return listOf()
    }
}