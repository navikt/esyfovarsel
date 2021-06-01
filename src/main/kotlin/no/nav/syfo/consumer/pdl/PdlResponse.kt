package no.nav.syfo.consumer.pdl

import java.io.Serializable

data class PdlIdentResponse(
    val errors: List<PdlError>?,
    val data: PdlHentIdenter?
)

data class PdlHentIdenter(
    val hentIdenter: PdlIdenter?
) : Serializable

data class PdlIdenter(
    val identer: List<PdlIdent>
) : Serializable

data class PdlIdent(
    val ident: String
) : Serializable

data class PdlError(
    val message: String,
    val locations: List<PdlErrorLocation>,
    val path: List<String>?,
    val extensions: PdlErrorExtension
)

data class PdlErrorLocation(
    val line: Int?,
    val column: Int?
)

data class PdlErrorExtension(
    val code: String?,
    val classification: String
)
