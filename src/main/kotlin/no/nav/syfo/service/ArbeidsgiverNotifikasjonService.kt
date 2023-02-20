package no.nav.syfo.service

import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.narmesteLeder.NarmesteLederService
import no.nav.syfo.producer.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProdusent
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class ArbeidsgiverNotifikasjonService(
    val arbeidsgiverNotifikasjonProdusent: ArbeidsgiverNotifikasjonProdusent,
    val narmesteLederService: NarmesteLederService,
    val dineSykmeldteUrl: String,
) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.service.ArbeidsgiverNotifikasjonService")

    enum class LinkDestination {
        DINESYKMELDTE,
        OPPFOLGINGSPLAN,
        DIALOGMOTE
    }

    private fun urlForLinkDestination(linkDestination: LinkDestination, narmesteLederId: String): String {
        return when (linkDestination) {
            LinkDestination.DIALOGMOTE -> "$dineSykmeldteUrl/dialogmoter/$narmesteLederId"
            LinkDestination.OPPFOLGINGSPLAN -> "$dineSykmeldteUrl/oppfolgingsplaner/$narmesteLederId"
            else -> "$dineSykmeldteUrl/$narmesteLederId"
        }
    }

    fun sendNotifikasjon(
        arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjonInput,
        linkDestination: LinkDestination
    ) {
        runBlocking {
            val narmesteLederRelasjon = narmesteLederService.getNarmesteLederRelasjon(
                arbeidsgiverNotifikasjon.ansattFnr,
                arbeidsgiverNotifikasjon.virksomhetsnummer
            )

            if (narmesteLederRelasjon == null || !narmesteLederService.hasNarmesteLederInfo(narmesteLederRelasjon)) {
                log.warn("Sender ikke varsel til ag-notifikasjon: narmesteLederRelasjon er null eller har ikke kontaktinfo")
                return@runBlocking
            }

            if (arbeidsgiverNotifikasjon.narmesteLederFnr !== null && !arbeidsgiverNotifikasjon.narmesteLederFnr.equals(
                    narmesteLederRelasjon.narmesteLederFnr
                )
            ) {
                log.warn("Sender ikke varsel til ag-notifikasjon: den ansatte har nærmeste leder med annet fnr enn mottaker i varselHendelse")
                return@runBlocking
            }

            if (narmesteLederRelasjon.narmesteLederId === null) {
                log.warn("Sender ikke varsel til ag-notifikasjon: Mangler nærmeste leder ID")
                return@runBlocking
            }

            val arbeidsgiverNotifikasjonen = ArbeidsgiverNotifikasjon(
                arbeidsgiverNotifikasjon.uuid.toString(),
                arbeidsgiverNotifikasjon.virksomhetsnummer,
                urlForLinkDestination(linkDestination, narmesteLederRelasjon.narmesteLederId!!),
                narmesteLederRelasjon.narmesteLederFnr!!,
                arbeidsgiverNotifikasjon.ansattFnr,
                arbeidsgiverNotifikasjon.messageText,
                narmesteLederRelasjon.narmesteLederEpost!!,
                arbeidsgiverNotifikasjon.merkelapp,
                arbeidsgiverNotifikasjon.emailTitle,
                arbeidsgiverNotifikasjon.emailBody,
                arbeidsgiverNotifikasjon.hardDeleteDate,
            )
            arbeidsgiverNotifikasjonProdusent.createNewNotificationForArbeidsgiver(arbeidsgiverNotifikasjonen)
        }
    }
}

data class ArbeidsgiverNotifikasjonInput(
    val uuid: UUID,
    val virksomhetsnummer: String,
    val narmesteLederFnr: String?,
    val ansattFnr: String,
    val merkelapp: String,
    val messageText: String,
    val emailTitle: String,
    val emailBody: String,
    val hardDeleteDate: LocalDateTime,
)

