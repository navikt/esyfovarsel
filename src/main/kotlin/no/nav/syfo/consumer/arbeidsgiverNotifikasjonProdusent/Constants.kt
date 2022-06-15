package no.nav.syfo.consumer.arbeidsgiverNotifikasjonProdusent

const val MUTATION_PATH_PREFIX = "/arbeidsgiver-notifikasjon-produsent-api"
const val CREATE_NOTIFICATION_AG_MUTATION = "nyBeskjed.graphql"

const val MERKELAPP = "Oppfølging"
const val AKTIVITETSKRAV_MESSAGE_TEXT = "En av dine medarbeidere har snart vært sykmeldt i åtte uker"
const val AKTIVITETSKRAV_EMAIL_TITLE = "Varsel om nytt aktivitetskrav"
const val AKTIVITETSKRAV_EMAIL_BODY_START = "<body>En av dine medarbeidere har snart vært sykmeldt i åtte uker. Gå inn på "
const val AKTIVITETSKRAV_EMAIL_BODY_END = " for å se hvem det gjelder.</body>"

const val SVAR_MOTEBEHOV_MESSAGE_TEXT = "Vi trenger din vurdering av behovet for dialogmøte."
const val SVAR_MOTEBEHOV_EMAIL_TITLE = "Behov for dialogmøte?"
const val SVAR_MOTEBEHOV_EMAIL_BODY = "<body>Hei.<br>\n" +
    "NAV trenger din vurdering av behovet for dialogmøte, i forbindelse med en av dine ansatte sitt sykefravær.<br>\n" +
    "Du kan svare på møtebehovet ved å logge deg inn på “Min side - arbeidsgiver” og trykke på “Sykmeldte”. Da vil du også se hvem det gjelder.<br><br>\n" +
    "Har du spørsmål, kan du kontakte oss på 55 55 33 36.<br>\n" +
    "Vennlig hilsen NAV<br>\n" +
    "Du kan ikke svare på denne meldingen.</body>"
