package no.nav.syfo.kafka.consumers.varselbus.domain

import java.io.Serializable

data class MotebehovNLVarselData(
    val ansattFnr: String,
    val orgnummer: String
) : Serializable
