package no.nav.syfo.syketilfelle.domain

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.time.temporal.ChronoUnit

class Tidslinje(private val syketilfellebiter: Syketilfellebiter) {

    private val tidslinjeliste: List<Syketilfelledag>
    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syketilfelle.domain.Tidslinje")

    init {
        tidslinjeliste = genererTidslinje()
    }

    fun tidslinjeSomListe(): List<Syketilfelledag> {
        return tidslinjeliste
    }

    private fun genererTidslinje(): List<Syketilfelledag> {
        // TODO: Fjern try-catch når flex-syketilfellebiter topic er i sync med oppfølgingstilfeller
        try {
            require(syketilfellebiter.biter.isNotEmpty())
        } catch (e: IllegalArgumentException) {
            log.warn("Spør på fnr som ikke har noen syketilfellebiter i databasen")
        }

        val tidligsteFom = syketilfellebiter.finnTidligsteFom()
        val sensesteTom = syketilfellebiter.finnSenesteTom()

        return (0..ChronoUnit.DAYS.between(tidligsteFom, sensesteTom))
            .map(tidligsteFom::plusDays)
            .map(syketilfellebiter::tilSyketilfelleIntradag)
            .map(SyketilfelleIntradag::velgSyketilfelledag)
    }
}
