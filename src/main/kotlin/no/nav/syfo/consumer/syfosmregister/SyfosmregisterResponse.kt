package no.nav.syfo.consumer.syfosmregister

import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.syfo.consumer.domain.ArbeidsgiverStatus
import java.io.Serializable
import java.time.OffsetDateTime

data class SyfosmregisterResponse(
    val id: String? = null,
    val sykmeldingsperioder: List<SykmeldingPeriode> = listOf(),
    val sykmeldingStatus: SykmeldingStatus? = null
) : Serializable

@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
data class SykmeldingPeriode(
    val fom: String? = null,
    val tom: String? = null,
    val gradert: Gradert? = null
) : Serializable

data class Gradert(
    val grad: Int
) : Serializable

data class SykmeldingStatus(
    val statusEvent: String? = null,
    val timestamp: OffsetDateTime? = null,
    val arbeidsgiver: ArbeidsgiverStatus? = null
) : Serializable

data class Behandlingsutfall(
    val status: RegelStatus? = null,
    val ruleHits: List<Regelinfo?>? = null
) : Serializable

data class Regelinfo(
    val messageForSender: String? = null,
    val messageForUser: String? = null,
    val ruleName: String? = null,
    val ruleStatus: RegelStatus? = null
) : Serializable

enum class RegelStatus {
    OK, MANUAL_PROCESSING, INVALID
}
