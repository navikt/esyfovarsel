CREATE TABLE UTBETALING_INFOTRYGD
(
    uuid                       UUID PRIMARY KEY,
    fnr                        VARCHAR(11) NOT NULL,
    max_date                   DATE        NOT NULL,
    utbet_tom                  DATE        NOT NULL,
    gjenstaende_sykepengedager INTEGER     NOT NULL,
    opprettet                  TIMESTAMP   NOT NULL
);
