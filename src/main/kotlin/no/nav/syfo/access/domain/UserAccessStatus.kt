package no.nav.syfo.access.domain

data class UserAccessStatus(
    var fnr: String?,
    val canUserBeDigitallyNotified: Boolean,
)
