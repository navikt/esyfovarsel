package no.nav.syfo.exceptions

import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste
import java.io.IOException

class VeilederAlreadyBookedMeetingException : IllegalStateException(
    "Veileder har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da veilederen allerede skal ha kalt inn " +
        "til dialogmote."
)

class MotebehovAfterBookingException : IllegalStateException(
    "Veileder har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da syfomotebehov ikke skal sende varsel event " +
        "dersom veilder allerede har innkalt til dialogmøte."
)

class DuplicateMotebehovException : IllegalStateException(
    "Møtebehov har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da syfomotebehov ikke skal sende varsel event " +
        "flere ganger i samme sykefravær."
)

class JournalpostDistribusjonException(
    message: String,
    val uuid: String? = null,
    val journalpostId: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class JournalpostNetworkException(
    message: String,
    val uuid: String,
    val journalpostId: String,
    cause: Throwable
) : IOException(message, cause)
