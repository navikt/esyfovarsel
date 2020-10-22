package no.nav.syfo.domain

open class Hendelse {
    var type: Hendelsestype? = null

    open fun withType(type: Hendelsestype?): Hendelse {
        this.type = type
        return this
    }
}