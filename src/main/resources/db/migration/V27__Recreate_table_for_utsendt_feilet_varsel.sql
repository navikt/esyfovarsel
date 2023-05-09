CREATE TABLE UTSENDING_VARSEL_FEILET
(
    uuid                               UUID PRIMARY KEY,
    uuid_ekstern_referanse             VARCHAR(50),
    arbeidstaker_fnr                   VARCHAR(11)  NOT NULL,
    narmesteleder_fnr                  VARCHAR(11),
    orgnummer                          VARCHAR(9),
    hendelsetype_navn                  VARCHAR(100) NOT NULL,
    arbeidsgivernotifikasjon_merkelapp VARCHAR(50),
    brukernotifikasjoner_melding_type  VARCHAR(50),
    journalpost_id                     VARCHAR(50),
    kanal                              VARCHAR(50)  NOT NULL,
    feilmelding                        VARCHAR(100),
    utsendt_forsok_tidspunkt           TIMESTAMP    NOT NULL
);
