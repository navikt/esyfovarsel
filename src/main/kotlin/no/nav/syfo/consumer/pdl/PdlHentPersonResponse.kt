package no.nav.syfo.consumer.pdl

import capitalizeFirstLetter

data class HentPersonResponse(
    val errors: List<PdlError>?,
    val data: HentPersonData
)

data class HentPersonData(
    val hentPerson: HentPerson
)

fun HentPersonData.getFodselsdato(): String? {
    return this.hentPerson.foedselsdato.first().foedselsdato
}

fun HentPersonData.isPersonDod(): Boolean {
    return this.hentPerson.doedsfall.first().doedsdato != null
}

data class HentPerson(
    val foedselsdato: List<Foedselsdato>,
    val navn: List<Navn>,
    val doedsfall: List<Doedsdato>,
)

data class Doedsdato(
    val doedsdato: String?,
)

data class Foedselsdato(
    val foedselsdato: String?
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

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

fun PdlError.errorMessage(): String {
    return "${this.message} with code: ${extensions.code} and classification: ${extensions.classification}"
}

fun HentPersonData.firstName(): String? {
    val nameList = this.hentPerson.navn
    return nameList.firstOrNull()?.fornavn?.capitalizeFirstLetter()
}

fun HentPersonData.fullName(): String? {
    val nameList = this.hentPerson.navn
    return nameList.firstOrNull()?.let {
        val firstName = it.fornavn.capitalizeFirstLetter()
        val middleName = it.mellomnavn?.capitalizeFirstLetter()
        val surName = it.etternavn.capitalizeFirstLetter()

        if (middleName.isNullOrBlank()) {
            "$firstName $surName"
        } else {
            "$firstName ${middleName.capitalizeFirstLetter()} $surName"
        }
    }
}
