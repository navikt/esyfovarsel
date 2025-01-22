DROP TABLE IF EXISTS ARBEIDSGIVERNOTIFIKASJONER_SAK;
CREATE TABLE ARBEIDSGIVERNOTIFIKASJONER_SAK
(
    id                     UUID PRIMARY KEY,
    narmestelederId        VARCHAR(50)  NOT NULL,
    grupperingsid          VARCHAR(50)  NOT NULL,
    merkelapp              VARCHAR(50)  NOT NULL,
    virksomhetsnummer      VARCHAR(20)  NOT NULL,
    narmesteLederFnr       VARCHAR(11)  NOT NULL,
    ansattFnr              VARCHAR(11)  NOT NULL,
    tittel                 VARCHAR(255) NOT NULL,
    tilleggsinformasjon    TEXT,
    lenke                  TEXT         NOT NULL,
    initiellStatus         VARCHAR(50)  NOT NULL,
    nesteSteg              VARCHAR(255),
    overstyrStatustekstMed TEXT,
    hardDeleteDate         TIMESTAMP    NOT NULL,
    opprettet              TIMESTAMP    NOT NULL,
    UNIQUE (grupperingsid, merkelapp)
);

CREATE INDEX idx_arbeidsgivernotifikasjoner_sak_merkelapp ON ARBEIDSGIVERNOTIFIKASJONER_SAK (merkelapp);
CREATE INDEX idx_arbeidsgivernotifikasjoner_sak_grupperingsid ON ARBEIDSGIVERNOTIFIKASJONER_SAK (grupperingsid);

DROP TABLE IF EXISTS ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE;
CREATE TABLE ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE
(
    id                     UUID PRIMARY KEY,
    eksternId              TEXT        NOT NULL,
    sakId                  TEXT        NOT NULL,
    grupperingsid          TEXT        NOT NULL,
    merkelapp              VARCHAR(50)  NOT NULL,
    kalenderId             TEXT        NOT NULL,
    tekst                  TEXT        NOT NULL,
    startTidspunkt         TIMESTAMP   NOT NULL,
    sluttTidspunkt         TIMESTAMP,
    kalenderavtaleTilstand VARCHAR(50) NOT NULL,
    hardDeleteDate         TIMESTAMP,
    opprettet              TIMESTAMP   NOT NULL
);

CREATE INDEX idx_arbeidsgivernotifikasjoner_kalenderavtale_sakid ON ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE (sakId);