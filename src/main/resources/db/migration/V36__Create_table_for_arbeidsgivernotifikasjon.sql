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
    sakStatus              VARCHAR(50)              NOT NULL,
    hardDeleteDate         TIMESTAMP                NOT NULL,
    PRIMARY KEY (grupperingsid),
    UNIQUE (grupperingsid, merkelapp)
);


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
