DROP TABLE IF EXISTS ARBEIDSGIVERNOTIFIKASJONER_SAK;
CREATE TABLE ARBEIDSGIVERNOTIFIKASJONER_SAK
(
    grupperingsid          VARCHAR(50)              NOT NULL,
    merkelapp              VARCHAR(50)              NOT NULL,
    virksomhetsnummer      VARCHAR(20)              NOT NULL,
    narmesteLederFnr       VARCHAR(11)              NOT NULL,
    ansattFnr              VARCHAR(11)              NOT NULL,
    tittel                 VARCHAR(255)             NOT NULL,
    tilleggsinformasjon    TEXT,
    lenke                  TEXT                     NOT NULL,
    initiellStatus         VARCHAR(50)              NOT NULL,
    nesteSteg              VARCHAR(255),
    tidspunkt              TIMESTAMP WITH TIME ZONE NOT NULL,
    overstyrStatustekstMed TEXT,
    hardDeleteDate         TIMESTAMP                NOT NULL,
    PRIMARY KEY (grupperingsid),
    UNIQUE (grupperingsid, merkelapp)
);

CREATE INDEX idx_arbeidsgivernotifikasjoner_sak_merkelapp ON ARBEIDSGIVERNOTIFIKASJONER_SAK (merkelapp);
CREATE INDEX idx_arbeidsgivernotifikasjoner_sak_grupperingsid ON ARBEIDSGIVERNOTIFIKASJONER_SAK (grupperingsid);

DROP TABLE IF EXISTS ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE;
CREATE TABLE ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE
(
    eksternId              UUID PRIMARY KEY,
    virksomhetsnummer      VARCHAR(20) NOT NULL,
    grupperingsid          VARCHAR(50) NOT NULL,
    merkelapp              VARCHAR(50) NOT NULL,
    tekst                  TEXT        NOT NULL,
    narmesteLederFnr       VARCHAR(11) NOT NULL,
    ansattFnr              VARCHAR(11) NOT NULL,
    lenke                  TEXT        NOT NULL,
    startTidspunkt         TIMESTAMP   NOT NULL,
    sluttTidspunkt         TIMESTAMP,
    kalenderavtaleTilstand VARCHAR(50) NOT NULL,
    hardDeleteTidspunkt    TIMESTAMP   NOT NULL,
    UNIQUE (grupperingsid, merkelapp)
);

CREATE INDEX idx_arbeidsgivernotifikasjoner_kalenderavtale_merkelapp ON ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE (merkelapp);
CREATE INDEX idx_arbeidsgivernotifikasjoner_kalenderavtale_grupperingsid ON ARBEIDSGIVERNOTIFIKASJONER_KALENDERAVTALE (grupperingsid);