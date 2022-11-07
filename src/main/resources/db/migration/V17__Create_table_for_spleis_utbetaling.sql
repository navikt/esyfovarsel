CREATE TABLE UTBETALING_SPLEIS
(
    ID                       UUID PRIMARY KEY,
    FNR                      VARCHAR(11) NOT NULL,
    ORGANISASJONSNUMMER      VARCHAR(30) NOT NULL,
    EVENT                    VARCHAR(20) NOT NULL,
    TYPE                     VARCHAR(30) NOT NULL,
    FORELOPIG_BEREGNET_SLUTT DATE        NOT NULL,
    FORBRUKTE_SYKEDAGER      INT         NOT NULL,
    GJENSTAENDE_SYKEDAGER    INT         NOT NULL,
    STONADSDAGER             INT         NOT NULL,
    ANTALL_VEDTAK            INT         NOT NULL,
    FOM                      DATE        NOT NULL,
    TOM                      DATE        NOT NULL,
    UTBETALING_ID            VARCHAR(50) NOT NULL UNIQUE,
    KORRELASJON_ID           VARCHAR(50) NOT NULL,
    OPPRETTET                TIMESTAMP   NOT NULL
);
