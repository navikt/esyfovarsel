package no.nav.syfo.access.domain

data class UserAccessStatus(
    var fnr: String?,
    val canUserBeDigitallyNotified: Boolean,
    val canUserBePhysicallyNotified: Boolean,
)

fun UserAccessStatus.canUserBeNotified(): Boolean = canUserBeDigitallyNotified || canUserBePhysicallyNotified
