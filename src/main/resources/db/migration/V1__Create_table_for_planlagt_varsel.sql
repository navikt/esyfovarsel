CREATE TABLE PLANLAGT_VARSEL (
  uuid                     UUID               PRIMARY KEY,
  fnr                      VARCHAR(11)        NOT NULL,
  aktor_id                 VARCHAR(13)        NOT NULL,
  type                     VARCHAR(100)       NOT NULL,
  utsendingsdato           DATE               NOT NULL,
  opprettet                TIMESTAMP          NOT NULL,
  sist_endret              TIMESTAMP          NOT NULL
);
