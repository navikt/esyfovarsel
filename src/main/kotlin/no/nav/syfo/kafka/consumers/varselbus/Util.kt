package no.nav.syfo.kafka.consumers.varselbus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
fun isOrgFnrNrValidFormat(fnr: String?, orgnr: String?): Boolean {
    return orgnr?.length == 9 &&
        orgnr.all { char -> char.isDigit() } &&
        fnr?.length == 11 &&
        fnr.all { char -> char.isDigit() }
}
