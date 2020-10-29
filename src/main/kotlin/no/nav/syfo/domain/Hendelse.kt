package no.nav.syfo.domain

import java.time.LocalDate

interface Hendelse {
    var sykmelding: Sykmelding?
    var type: HendelseType
    var intruffetdato: LocalDate

    fun withInntruffetdato(intruffetdato: LocalDate): Hendelse
    fun withSykmelding(sykmelding: Sykmelding): Hendelse
    fun withType(type: HendelseType): Hendelse
}
