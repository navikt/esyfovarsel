package no.nav.syfo.consumer.syfosmregister

import java.io.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.ArbeidsgiverDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.BehandlerDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.BehandlingsutfallDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.KontaktMedPasientDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.MedisinskVurderingDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.MeldingTilNavDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.MerknadDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.PrognoseDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.SporsmalSvarDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.SykmeldingStatusDTO
import no.nav.syfo.consumer.syfosmregister.sykmeldingModel.SykmeldingsperiodeDTO

data class SykmeldingDTO(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val behandlingsutfall: BehandlingsutfallDTO,
    val legekontorOrgnummer: String?,
    val arbeidsgiver: ArbeidsgiverDTO?,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val sykmeldingStatus: SykmeldingStatusDTO,
    val medisinskVurdering: MedisinskVurderingDTO?,
    val skjermesForPasient: Boolean,
    val prognose: PrognoseDTO?,
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvarDTO>>,
    val tiltakArbeidsplassen: String?,
    val tiltakNAV: String?,
    val andreTiltak: String?,
    val meldingTilNAV: MeldingTilNavDTO?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasientDTO,
    val behandletTidspunkt: OffsetDateTime,
    val behandler: BehandlerDTO,
    val syketilfelleStartDato: LocalDate?,
    val navnFastlege: String?,
    val egenmeldt: Boolean?,
    val papirsykmelding: Boolean?,
    val harRedusertArbeidsgiverperiode: Boolean?,
    val merknader: List<MerknadDTO>?,
) : Serializable
