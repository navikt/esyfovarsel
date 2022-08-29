package no.nav.syfo.access.domain

data class UserAccessStatus(
    var fnr: String?,
    val canUserBeDigitallyNotified: Boolean,
    val canUserBePhysicallyNotified: Boolean,
    val isKode6Eller7: Boolean?, // har adressebeskyttelse
    val isKanVarsles: Boolean?, // status i KRR: [reservert/ikke reservert + kontakt info nyere enn 18mnd]
)
