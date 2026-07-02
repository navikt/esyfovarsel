package no.nav.syfo.producer.arbeidsgivernotifikasjon

import com.apollo.graphql.NySakMutation
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverDeleteNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.ArbeidsgiverNotifikasjon
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyKalenderInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.NyStatusSakInput
import no.nav.syfo.producer.arbeidsgivernotifikasjon.domain.OppdaterKalenderInput
import org.slf4j.LoggerFactory
import java.util.UUID

class FakeArbeidsgiverNotifikasjonProdusent : IArbeidsgiverNotifikasjonProdusent {
    private val log = LoggerFactory.getLogger(FakeArbeidsgiverNotifikasjonProdusent::class.qualifiedName)

    override suspend fun createNewOppgaveForArbeidsgiver(arbeidsgiverNotifikasjon: ArbeidsgiverNotifikasjon): String =
        UUID.randomUUID().toString().also { id ->
            log.info(
                "Lokal arbeidsgivernotifikasjon oppgave opprettet: merkelapp=${arbeidsgiverNotifikasjon.merkelapp}, virksomhetsnummer=${arbeidsgiverNotifikasjon.virksomhetsnummer}, id=$id",
            )
        }

    override suspend fun createNewBeskjedForArbeidsgiver(arbeidsgiverNotifikasjonInput: ArbeidsgiverNotifikasjon): String =
        UUID.randomUUID().toString().also { id ->
            log.info(
                "Lokal arbeidsgivernotifikasjon beskjed opprettet: merkelapp=${arbeidsgiverNotifikasjonInput.merkelapp}, virksomhetsnummer=${arbeidsgiverNotifikasjonInput.virksomhetsnummer}, id=$id, messageText=${arbeidsgiverNotifikasjonInput.messageText}, epostTitle=${arbeidsgiverNotifikasjonInput.emailTitle}, epostBody=${arbeidsgiverNotifikasjonInput.emailBody}, url=${arbeidsgiverNotifikasjonInput.url}",
            )
        }

    override suspend fun createNewSak(mutation: NySakMutation): String =
        UUID.randomUUID().toString().also { id ->
            log.info(
                "Lokal arbeidsgivernotifikasjon sak opprettet: grupperingsid=${mutation.grupperingsid}, merkelapp=${mutation.merkelapp}, virksomhetsnummer=${mutation.virksomhetsnummer}, id=$id",
            )
        }

    override suspend fun nyStatusSak(nyStatusSakInput: NyStatusSakInput): String =
        UUID.randomUUID().toString().also { id ->
            log.info(
                "Lokal arbeidsgivernotifikasjon sakstatus oppdatert: merkelapp=${nyStatusSakInput.merkelapp}, status=${nyStatusSakInput.sakStatus}, id=$id, hardDeleteDate=${nyStatusSakInput.oppdatertHardDeleteDateTime}",
            )
        }

    override suspend fun createNewKalenderavtale(nyKalenderInput: NyKalenderInput): String =
        UUID.randomUUID().toString().also { id ->
            log.info(
                "Lokal arbeidsgivernotifikasjon kalenderavtale opprettet: merkelapp=${nyKalenderInput.merkelapp}, virksomhetsnummer=${nyKalenderInput.virksomhetsnummer}, status=${nyKalenderInput.kalenderavtaleTilstand}, id=$id",
            )
        }

    override suspend fun updateKalenderavtale(oppdaterKalenderInput: OppdaterKalenderInput): String =
        UUID.randomUUID().toString().also { id ->
            log.info(
                "Lokal arbeidsgivernotifikasjon kalenderavtale oppdatert: status=${oppdaterKalenderInput.nyTilstand}, id=$id",
            )
        }

    override suspend fun deleteNotifikasjonForArbeidsgiver(arbeidsgiverDeleteNotifikasjon: ArbeidsgiverDeleteNotifikasjon) {
        log.info(
            "Lokal arbeidsgivernotifikasjon slettet: merkelapp=${arbeidsgiverDeleteNotifikasjon.merkelapp}, eksternReferanse=${arbeidsgiverDeleteNotifikasjon.eksternReferanse}",
        )
    }
}
