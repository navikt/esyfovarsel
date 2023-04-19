CREATE TABLE UTSENDT_VARSEL_FEILET
(
    uuid                     UUID PRIMARY KEY,
    narmesteleder_fnr        VARCHAR(11),
    fnr                      VARCHAR(11)  NOT NULL,
    orgnummer                varchar(9),
    type                     VARCHAR(100) NOT NULL,
    kanal                    varchar(50),
    feilmelding              varchar(100),
    journalpost_id           varchar(50),
    utsendt_forsok_tidspunkt TIMESTAMP    NOT NULL,
    ekstern_referanse        varchar(50)
);
