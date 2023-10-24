package no.nav.syfo.utils

import no.nav.syfo.kafka.consumers.varselbus.domain.HendelseType
import no.nav.syfo.kafka.producers.minsideMikrofrontend.Tjeneste

class VeilederAlreadyBookedMeetingException : IllegalStateException(
    "Veileder har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da veilederen allerede skal ha kalt inn " +
        "til dialogmote.",
)

class MotebehovAfterBookingException : IllegalStateException(
    "Veileder har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da syfomotebehov ikke skal sende varsel event " +
        "dersom veilder allerede har innkalt til dialogmøte.",
)

class DuplicateMotebehovException : IllegalStateException(
    "Møtebehov har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.DIALOGMOTE}. Dette er uforventet da syfomotebehov ikke skal sende varsel event " +
        "flere ganger i samme sykefravær.",
)

class DuplicateAktivitetskravException(hendelseType: HendelseType) : IllegalStateException(
    "Hendelse $hendelseType har allerede aktivert mikrofrontend for tjenesten: " +
        "${Tjeneste.AKTIVITETSKRAV}. Dette er uforventet da det ikke skal komme flere ganger før forrige er skrudd av",
)
