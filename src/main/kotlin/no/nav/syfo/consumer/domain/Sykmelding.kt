package no.nav.syfo.consumer.domain

data class Sykmelding(
    val id: String,
    val sykmeldingsperioder: List<SykmeldingPeriode>,
    val sykmeldingStatus: SykmeldingStatus
)
