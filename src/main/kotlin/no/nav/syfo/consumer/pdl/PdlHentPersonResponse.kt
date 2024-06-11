package no.nav.syfo.consumer.pdl

data class HentPersonResponse(
    val data: HentPersonData
)

data class HentPersonData(
    val hentPerson: HentPerson
)

fun HentPersonData.getFullNameAsString(): String {
    val navn = this.hentPerson.navn.first()

    return "${navn.fornavn}${getMellomnavn(navn.mellomnavn)} ${navn.etternavn}"
}

private fun getMellomnavn(mellomnavn: String?): String {
    return if (mellomnavn !== null) " $mellomnavn" else ""
}

data class HentPerson(
    val foedselsdato: Foedselsdato,
    val navn: List<Navn>
)

data class Foedselsdato(
    val foedselsdato: String?
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)
