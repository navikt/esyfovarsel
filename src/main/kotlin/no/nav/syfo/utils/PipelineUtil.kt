package no.nav.syfo.utils

import io.ktor.application.*
import no.nav.syfo.consumer.dkif.DkifConsumer.Companion.NAV_PERSONIDENT_HEADER
import no.nav.syfo.domain.PersonIdent

fun ApplicationCall.getPersonIdent(): PersonIdent? =
    this.request.headers[NAV_PERSONIDENT_HEADER]?.let { PersonIdent(it) }
