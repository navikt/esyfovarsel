package no.nav.syfo.arbeidstakervarsel.domain

data class ArbeidstakerVarsel(
    val mottakerFnr: String,
    val brukernotifikasjonVarsel: BrukernotifikasjonVarsel? = null,
    val dokumentDistribusjonVarsel: DokumentdistribusjonVarsel? = null,
    val dittSykefravaerVarsel: DittSykefravaerVarsel? = null,
    val microfrontendEvent: MicrofrontendEvent? = null,
)
