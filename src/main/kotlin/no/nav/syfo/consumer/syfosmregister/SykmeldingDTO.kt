package no.nav.syfo.consumer.syfosmregister

import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.SykmeldingStatusDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.SykmeldingsperiodeDTO
import java.io.Serializable

data class SykmeldingDTO(
    val id: String,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val sykmeldingStatus: SykmeldingStatusDTO,
) : Serializable
