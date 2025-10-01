package no.nav.syfo.kafka.consumers.arbeidstakervarsel.domain

data class ArbeidstakerVarsel(
    val mottakerFnr: String,
    val brukernotifikasjonVarsel: BrukernotifikasjonVarsel? = null,
    val dokumentDistribusjonVarsel: DokumentdistribusjonVarsel? = null,
    val dinesykmeldteVarsel: DineSykmeldteVarsel? = null,
    val microfrontend: Microfrontend? = null,
)
