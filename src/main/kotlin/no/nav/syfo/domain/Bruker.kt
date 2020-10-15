package no.nav.syfo.domain

class Bruker {
    var aktoerId: String = ""

    fun withAktoerId(aktoerId: String): Bruker {
        this.aktoerId = aktoerId
        return this
    }
}
