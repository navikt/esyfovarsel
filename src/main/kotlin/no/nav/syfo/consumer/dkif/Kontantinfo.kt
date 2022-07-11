package no.nav.syfo.consumer.domain

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class Kontaktinfo (
    val kanVarsles: Boolean?,
    val reservert: Boolean?,
)

object KontaktinfoMapper {

    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

    fun map(json: String, aktorId: String): Kontaktinfo? {
        val jsonNode = objectMapper.readTree(json)

        if (jsonNode.has("feil")) {
            return null
        }

        jsonNode.path("kontaktinfo").path(aktorId).let {
            return Kontaktinfo(
                it["kanVarsles"]?.asBoolean(),
                it["reservert"]?.asBoolean()
            )
        }
    }

    fun mapPerson(json: String): Kontaktinfo? {
        val jsonNode = objectMapper.readTree(json)

        if (jsonNode.has("feil")) {
            return null
        }

        jsonNode.let {
            return Kontaktinfo(
                it["kanVarsles"]?.asBoolean(),
                it["reservert"]?.asBoolean()
            )
        }
    }
}
