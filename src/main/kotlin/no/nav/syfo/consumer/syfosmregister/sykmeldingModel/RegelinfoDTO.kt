package no.nav.syfo.consumer.syfosmregister.sykmeldingModel

data class RegelinfoDTO(
    val messageForSender: String,
    val messageForUser: String,
    val ruleName: String,
    val ruleStatus: RegelStatusDTO?,
)
