package no.nav.syfo.consumer.syfosmregister.sykmeldingModel

data class BehandlingsutfallDTO(
    val status: RegelStatusDTO,
    val ruleHits: List<RegelinfoDTO>
)
