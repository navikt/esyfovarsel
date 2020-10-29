package no.nav.syfo.domain

import java.time.LocalDate

abstract class Hendelse {
    abstract val type: Hendelsestype
    abstract val inntruffetdato: LocalDate
}