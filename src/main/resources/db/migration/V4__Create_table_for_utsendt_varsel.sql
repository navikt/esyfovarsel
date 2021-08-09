CREATE TABLE UTSENDT_VARSEL (
  uuid                     UUID               PRIMARY KEY,
  fnr                      VARCHAR(11)        NOT NULL,
  aktor_id                 VARCHAR(13)        NOT NULL,
  type                     VARCHAR(100)       NOT NULL,
  utsendt_tidspunkt        TIMESTAMP          NOT NULL
);
