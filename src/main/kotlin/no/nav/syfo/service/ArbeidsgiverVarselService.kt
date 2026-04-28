package no.nav.syfo.service

import com.fasterxml.jackson.databind.JsonMappingException
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidsgiverNotifikasjonTilAltinnRessursHendelse
import no.nav.syfo.kafka.consumers.varselbus.domain.toVarselData
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ArbeidsgiverVarselService {
    private val log: Logger = LoggerFactory.getLogger(ArbeidsgiverVarselService::class.qualifiedName)

    suspend fun sendVarselTilArbeidsgiver(hendelse: ArbeidsgiverNotifikasjonTilAltinnRessursHendelse) {
        val data =
            requireNotNull(hendelse.data) {
                "ArbeidsgiverHendelse mangler feltet: data"
            }
        val varselData =
            runCatching {
                data.toVarselData()
            }.onFailure { exception ->
                log.warn(
                    "ArbeidsgiverVarselService stub klarte ikke å parse varselData for type={}, orgnummer={}, ressursId={}",
                    hendelse.type,
                    hendelse.orgnummer,
                    hendelse.ressursId,
                    exception.toVarselDataPath(),
                )
            }.getOrElse { exception ->
                throw IllegalArgumentException(
                    "ArbeidsgiverHendelse har ugyldig format i feltet: ${exception.toVarselDataPath()}",
                )
            }
        val notifikasjonInnhold =
            requireNotNull(varselData.notifikasjonInnhold) {
                "ArbeidsgiverHendelse mangler feltet: data.notifikasjonInnhold"
            }
        with(notifikasjonInnhold) {
            log.info(
                "ArbeidsgiverVarselService stub kalt: type={}, orgnummer={}, kilde={}, ressursId={}, ressursUrl={}, eksternRefereanseId={}, harEpostTittel={}, epostTittelLengde={}, harEpostBody={}, epostBodyLengde={}, harSmsTekst={}, smsTekstLengde={}",
                hendelse.type,
                hendelse.orgnummer,
                hendelse.kilde,
                hendelse.ressursId,
                hendelse.ressursUrl,
                hendelse.eksternReferanseId,
                epostTittel.isNotBlank(),
                epostTittel.length,
                epostBody.isNotBlank(),
                epostBody.length,
                smsTekst.isNotBlank(),
                smsTekst.length,
            )
        }
    }
}

private fun Throwable.toVarselDataPath(): String {
    val mappingPath =
        (this as? JsonMappingException)
            ?.path
            ?.let { path ->
                path.joinToString(".") { reference ->
                    reference.fieldName
                        ?: reference.index
                            .takeIf { it >= 0 }
                            ?.let { "[$it]" }
                            .orEmpty()
                }
            }
    if (mappingPath.isNullOrBlank()) {
        return "data"
    }
    return "data.$mappingPath"
}
