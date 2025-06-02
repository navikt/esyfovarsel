package no.nav.syfo.consumer.dkif

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class PostPersonerResponse(
    val personer: Map<String, Kontaktinfo> = mapOf(),
    val feil: Map<String, String> = mapOf(),
) {
    companion object {
        fun mapFromJson(json: String): PostPersonerResponse {
            val jsonMapper = jacksonObjectMapper().apply {
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
            return jsonMapper.readValue(json, PostPersonerResponse::class.java)
        }
    }
}
