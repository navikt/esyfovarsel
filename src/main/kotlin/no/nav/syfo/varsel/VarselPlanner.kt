package no.nav.syfo.varsel

import no.nav.syfo.consumer.domain.Oppfolgingstilfelle

interface VarselPlanner {
    fun planVarsel(oppfolgingstilfelle: Oppfolgingstilfelle)
}