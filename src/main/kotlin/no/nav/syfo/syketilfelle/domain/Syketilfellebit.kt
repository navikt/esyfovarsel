package no.nav.syfo.syketilfelle.domain

import no.nav.syfo.kafka.consumers.syketilfelle.domain.Syketilfelledag
import java.time.LocalDateTime
import no.nav.syfo.syketilfelle.ListContainsPredicate
import java.time.LocalDate

enum class Tag {
    SYKMELDING,
    NY,
    BEKREFTET,
    SENDT,
    KORRIGERT,
    AVBRUTT,
    UTGAATT,
    PERIODE,
    FULL_AKTIVITET,
    INGEN_AKTIVITET,
    GRADERT_AKTIVITET,
    BEHANDLINGSDAGER,
    BEHANDLINGSDAG,
    ANNET_FRAVAR,
    SYKEPENGESOKNAD,
    FERIE,
    PERMISJON,
    OPPHOLD_UTENFOR_NORGE,
    EGENMELDING,
    FRAVAR_FOR_SYKMELDING,
    PAPIRSYKMELDING,
    ARBEID_GJENNOPPTATT,
    KORRIGERT_ARBEIDSTID,
    UKJENT_AKTIVITET,
    UTDANNING,
    FULLTID,
    DELTID,
    REDUSERT_ARBEIDSGIVERPERIODE,
    REISETILSKUDD,
    AVVENTENDE
}

data class Syketilfellebit(
    val id: String? = null,
    val fnr: String? = null,
    val orgnummer: String? = null,
    val opprettet: LocalDateTime,
    val inntruffet: LocalDateTime,
    val tags: List<Tag>,
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val korrigererSendtSoknad: String? = null,
)

fun String.tagsFromString() = split(',').map(String::trim).map(Tag::valueOf)

class SyketilfelleIntradag(
    private val dag: LocalDate,
    private val biter: List<Syketilfellebit>,
    private val prioriteringsliste: List<ListContainsPredicate<Tag>>
) {

    fun velgSyketilfelledag(): Syketilfelledag {

        return biter
            .groupBy { it.inntruffet.toLocalDate() }
            .toSortedMap()
            .mapValues(this::finnPresedens)
            .mapNotNull(Map.Entry<LocalDate, Syketilfellebit?>::value)
            .map { it.toSyketilfelledag() }
            .lastOrNull() ?: Syketilfelledag(dag, null)
    }

    private fun finnPresedens(entry: Map.Entry<LocalDate, List<Syketilfellebit>>): Syketilfellebit? {
        val (_, syketilfeller) = entry

        prioriteringsliste.forEach { prioriteringselement ->
            return syketilfeller.find { it.tags in prioriteringselement } ?: return@forEach
        }

        return null
    }

    private fun Syketilfellebit.toSyketilfelledag(): Syketilfelledag =
        Syketilfelledag(
            dag = this@SyketilfelleIntradag.dag,
            prioritertSyketilfellebit = this
        )
}


class Syketilfellebiter(
    val biter: List<Syketilfellebit>,
    private val prioriteringsliste: List<ListContainsPredicate<Tag>>
) {
    fun tilSyketilfelleIntradag(dag: LocalDate): SyketilfelleIntradag {
        return SyketilfelleIntradag(dag, biter.filter { dag in (it.fom..(it.tom)) }, prioriteringsliste)
    }

    fun finnTidligsteFom(): LocalDate {
        return this.finnBit(Comparator.comparing { it.fom }).fom
    }

    fun finnSenesteTom(): LocalDate {
        return this.finnBit(Comparator.comparing<Syketilfellebit, LocalDate> { it.tom }.reversed()).tom
    }

    private fun finnBit(comparator: Comparator<Syketilfellebit>): Syketilfellebit {
        return biter.sortedWith(comparator).first()
    }
}
