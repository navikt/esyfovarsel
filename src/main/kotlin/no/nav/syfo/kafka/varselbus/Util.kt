package no.nav.syfo.kafka.varselbus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.kafka.varselbus.domain.DineSykmeldteHendelse
import no.nav.syfo.kafka.varselbus.domain.OppfolgingsplanNLVarselData
import no.nav.syfo.kafka.varselbus.domain.EsyfovarselHendelse
import no.nav.syfo.kafka.varselbus.domain.HendelseType
import no.nav.syfo.kafka.varselbus.domain.HendelseType.*
import no.nav.syfo.kafka.varselbus.domain.DineSykmeldteHendelse.*
import org.apache.commons.cli.MissingArgumentException
import java.io.IOException

const val NL_OPPFOLGINGSPLAN_SENDT_TIL_GODKJENNING_TEKST = "1 oppfølgingsplan til godkjenning"
const val NL_OPPFOLGINGSPLAN_OPPRETTET_TEKST = "1 oppfølgingsplan er påbegynt"

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
fun isOrgFnrNrValidFormat(fnr: String?, orgnr: String?): Boolean {
    return orgnr?.length == 9
        && orgnr.all { char -> char.isDigit()}
        && fnr?.length == 11
        && fnr.all { char -> char.isDigit()}
}