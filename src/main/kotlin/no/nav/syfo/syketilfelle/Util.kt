package no.nav.syfo.syketilfelle

import no.nav.syfo.kafka.oppfolgingstilfelle.domain.Syketilfelledag
import no.nav.syfo.syketilfelle.domain.Tag
import no.nav.syfo.syketilfelle.domain.Oppfolgingstilfelle
import java.time.LocalDate

class ListContainsPredicate<T> private constructor(private val predicate: (List<T>) -> Boolean) {

    companion object {
        fun <T> of(t: T) = ListContainsPredicate<T> { t in it }

        fun <T> tagsSize(size: Int) = ListContainsPredicate<T> { it.size == size }

        fun <T> not(t: T) = not(of(t))

        fun <T> not(lcp: ListContainsPredicate<T>) = ListContainsPredicate<T> { it !in lcp }
    }

    operator fun contains(listToTest: List<T>): Boolean = predicate(listToTest)

    infix fun and(other: T) = and(of(other))

    infix fun and(other: ListContainsPredicate<T>) = ListContainsPredicate<T> { it in this && it in other }

    infix fun or(other: T) = or(of(other))

    infix fun or(other: ListContainsPredicate<T>) = ListContainsPredicate<T> { it in this || it in other }

    operator fun not() = ListContainsPredicate<T> { it !in this }
}

infix fun <T> T.or(other: T) = ListContainsPredicate.of(this) or other

infix fun <T> T.and(other: ListContainsPredicate<T>) = ListContainsPredicate.of(this) and other

infix fun <T> T.and(other: T) = ListContainsPredicate.of(this) and other

operator fun <T> T.not() = ListContainsPredicate.not(this)


fun grupperIOppfolgingstilfeller(tidslinje: List<Syketilfelledag>): List<Oppfolgingstilfelle> {
    val oppfolgingstilfelleListe = ArrayList<Oppfolgingstilfelle>()
    var gjeldendeSyketilfelledagListe = ArrayList<Syketilfelledag>()
    var friskmeldtdagerSidenForrigeSykedag = 0
    var dagerAvArbeidsgiverperiode = 0
    var behandlingsdager = 0
    var sisteDagIArbeidsgiverperiode: Syketilfelledag? = null
    var sisteSykedagEllerFeriedagIOppfolgingstilfelle: LocalDate? = null

    tidslinje.forEach {
        when {
            it.erArbeidsdag() -> {
                friskmeldtdagerSidenForrigeSykedag++
            }

            it.erFeriedag() -> {
                sisteSykedagEllerFeriedagIOppfolgingstilfelle = it.dag
                if (friskmeldtdagerSidenForrigeSykedag > 0) {
                    // Vi teller kun feriedager her hvis man har vært tilbake på jobb
                    friskmeldtdagerSidenForrigeSykedag++
                }
                if (dagerAvArbeidsgiverperiode in 1..15) {
                    dagerAvArbeidsgiverperiode++
                    sisteDagIArbeidsgiverperiode = it
                }
            }
            else -> { // Er syk
                sisteSykedagEllerFeriedagIOppfolgingstilfelle = it.dag
                gjeldendeSyketilfelledagListe.add(it)
                friskmeldtdagerSidenForrigeSykedag = 0

                if (it.erBehandlingsdag()) {
                    behandlingsdager++
                }
                dagerAvArbeidsgiverperiode++

                if (dagerAvArbeidsgiverperiode <= 16) {
                    sisteDagIArbeidsgiverperiode = it
                }
            }
        }

        if (friskmeldtdagerSidenForrigeSykedag >= 16 && gjeldendeSyketilfelledagListe.isNotEmpty()) {
            val nyttOppfolgingstilfelle = Oppfolgingstilfelle(
                tidslinje = gjeldendeSyketilfelledagListe,
                sisteDagIArbeidsgiverperiode = sisteDagIArbeidsgiverperiode ?: it,
                dagerAvArbeidsgiverperiode = dagerAvArbeidsgiverperiode,
                behandlingsdager = behandlingsdager,
                sisteSykedagEllerFeriedag = sisteSykedagEllerFeriedagIOppfolgingstilfelle
            )
            oppfolgingstilfelleListe.add(nyttOppfolgingstilfelle)

            // Resett variabler
            gjeldendeSyketilfelledagListe = ArrayList()
            friskmeldtdagerSidenForrigeSykedag = 0
            dagerAvArbeidsgiverperiode = 0
            behandlingsdager = 0
            sisteDagIArbeidsgiverperiode = null
            sisteSykedagEllerFeriedagIOppfolgingstilfelle = null
        }
    }

    if (gjeldendeSyketilfelledagListe.isNotEmpty()) {
        val sisteOppfolgingstilfelle = Oppfolgingstilfelle(
            tidslinje = gjeldendeSyketilfelledagListe,
            sisteDagIArbeidsgiverperiode = sisteDagIArbeidsgiverperiode ?: tidslinje.last(),
            dagerAvArbeidsgiverperiode = dagerAvArbeidsgiverperiode,
            behandlingsdager = behandlingsdager,
            sisteSykedagEllerFeriedag = sisteSykedagEllerFeriedagIOppfolgingstilfelle
        )
        oppfolgingstilfelleListe.add(sisteOppfolgingstilfelle)
    }

    return oppfolgingstilfelleListe
}

private fun Syketilfelledag.erBehandlingsdag() =
    prioritertSyketilfellebit
        ?.tags
        ?.let { it in (Tag.SYKEPENGESOKNAD and Tag.BEHANDLINGSDAG) }
        ?: false

fun Syketilfelledag.erArbeidsdag() =
    prioritertSyketilfellebit
        ?.tags
        ?.let {
            it in (
                (Tag.SYKMELDING and Tag.PERIODE and Tag.FULL_AKTIVITET)
                    or
                        (Tag.SYKEPENGESOKNAD and Tag.ARBEID_GJENNOPPTATT)
                    or
                        (Tag.SYKEPENGESOKNAD and Tag.BEHANDLINGSDAGER)
                )
        }
        ?: true

fun Syketilfelledag.erFeriedag() =
    prioritertSyketilfellebit
        ?.tags
        ?.let { it in (Tag.SYKEPENGESOKNAD and (Tag.FERIE or Tag.PERMISJON)) }
        ?: false

fun Syketilfelledag.erSendt() =
    prioritertSyketilfellebit
        ?.tags
        ?.let { it in (Tag.SENDT or Tag.BEKREFTET) }
        ?: false
