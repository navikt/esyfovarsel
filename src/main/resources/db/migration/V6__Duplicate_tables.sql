CREATE TABLE UTSENDT_VARSEL_NEW (
  uuid                     UUID               PRIMARY KEY,
  fnr                      VARCHAR(11)        NOT NULL,
  aktor_id                 VARCHAR(13)        NOT NULL,
  type                     VARCHAR(100)       NOT NULL,
  utsendt_tidspunkt        TIMESTAMP          NOT NULL,
  planlagt_varsel_id       UUID
);

CREATE TABLE PLANLAGT_VARSEL_NEW (
  uuid                     UUID               PRIMARY KEY,
  fnr                      VARCHAR(11)        NOT NULL,
  aktor_id                 VARCHAR(13)        NOT NULL,
  type                     VARCHAR(100)       NOT NULL,
  utsendingsdato           DATE               NOT NULL,
  opprettet                TIMESTAMP          NOT NULL,
  sist_endret              TIMESTAMP          NOT NULL
);

CREATE TABLE SYKMELDING_IDS_NEW (
    uuid            UUID       PRIMARY KEY,
    sykmelding_id   VARCHAR    NOT NULL,
    varsling_id     VARCHAR    NOT NULL
);

ALTER TABLE SYKMELDING_IDS_NEW
    ALTER COLUMN varsling_id TYPE uuid USING varsling_id::uuid;

ALTER TABLE SYKMELDING_IDS_NEW
    ADD FOREIGN KEY (varsling_id)
        REFERENCES PLANLAGT_VARSEL_NEW (uuid) ON DELETE CASCADE;