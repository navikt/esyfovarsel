package no.nav.syfo.utils

import no.nav.syfo.kafka.producers.mineside_microfrontend.Tjeneste

class VeilederAlreadyBookedMeetingException : IllegalStateException(
    "Veileder har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da veilederen allerede skal ha kalt inn" +
        "til dialogmote."
)

class MotebehovAfterBookingException : IllegalStateException(
    "Veileder har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da syfomotebehov ikke skal sende varsel event" +
        "dersom veilder allerede har innkalt til dialogmøte."
)

class DuplicateMotebehovException : IllegalStateException(
    "Møtebehov har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da syfomotebehov ikke skal sende varsel event" +
        "flere ganger i samme sykefravær."
)
