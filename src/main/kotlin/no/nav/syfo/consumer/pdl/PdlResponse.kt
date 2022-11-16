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

data class PdlPersonResponse(
    val errors: List<PdlError>?,
    val data: PdlHentPerson?
) : Serializable

data class PdlHentPerson(
    val hentPerson: PdlPerson?
) : Serializable

data class PdlPerson(
    val adressebeskyttelse: List<Adressebeskyttelse>?,
    val navn: List<PersonNavn>?,
    val foedsel: List<PdlFoedsel>?
) : Serializable

data class PdlFoedsel(val foedselsdato: String?)

data class PersonNavn(
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
)

data class Adressebeskyttelse(
    val gradering: Gradering
) : Serializable

enum class Gradering : Serializable {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG
}

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

fun PdlHentPerson.isKode6Eller7(): Boolean {
    val adressebeskyttelse = this.hentPerson?.adressebeskyttelse
    return if (adressebeskyttelse.isNullOrEmpty()) {
        false
    } else {
        return adressebeskyttelse.any {
            it.isKode6() || it.isKode7()
        }
    }
}

fun Adressebeskyttelse.isKode6(): Boolean {
    return this.gradering == Gradering.STRENGT_FORTROLIG || this.gradering == Gradering.STRENGT_FORTROLIG_UTLAND
}

fun Adressebeskyttelse.isKode7(): Boolean {
    return this.gradering == Gradering.FORTROLIG
}

fun PdlHentPerson.getFullNameAsString(): String? {
    val navn = this.hentPerson?.navn?.first()

    return if (navn == null) {
        null
    } else {
        "${navn.fornavn}${getMellomnavn(navn.mellomnavn)} ${navn.etternavn}"
    }
}

fun PdlHentPerson.getFodselsdato(): String? {
    return this.hentPerson?.foedsel?.first()?.foedselsdato
}

private fun getMellomnavn(mellomnavn: String?): String {
    return if (mellomnavn !== null) " $mellomnavn" else ""
}
