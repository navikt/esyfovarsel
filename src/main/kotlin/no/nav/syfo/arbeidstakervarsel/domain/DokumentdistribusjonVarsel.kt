package no.nav.syfo.arbeidstakervarsel.domain

import no.nav.syfo.consumer.distribuerjournalpost.DistibusjonsType

data class DokumentdistribusjonVarsel(
    val uuid: String,
    val journalpostId: String,
    val distribusjonsType: DistibusjonsType = DistibusjonsType.ANNET,
    val dagerTilDeaktivering: Long? = null,
    val tvingSentralPrint: Boolean = false,
    val scheduledRetry: Boolean = true,
    val sendKunHvisReservert: Boolean = true,
)