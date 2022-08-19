package no.nav.syfo.service

import no.nav.syfo.kafka.consumers.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker

interface SyketilfelleInterface {
    suspend fun getOppfolgingstilfelle39UkerCommon(fnr: String, aktorId: String): Oppfolgingstilfelle39Uker?
}
