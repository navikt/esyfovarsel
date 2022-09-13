package no.nav.syfo.kafka.producers.dittsykefravaer.domain

import java.util.*

data class DittSykefravaerVarsel(
    val uuid: String = "${UUID.randomUUID()}",
    val melding: DittSykefravaerMelding
)
