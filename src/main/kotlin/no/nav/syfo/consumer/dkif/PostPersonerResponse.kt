package no.nav.syfo.consumer.dkif

data class PostPersonerResponse(
    val personer: Map<String, Kontaktinfo> = mapOf(),
    val feil: Map<String, String> = mapOf(),
)
