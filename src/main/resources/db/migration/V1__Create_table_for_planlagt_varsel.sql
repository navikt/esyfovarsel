CREATE SEQUENCE PLANLAGT_VARSEL_ID_SEQ;

CREATE TABLE PLANLAGT_VARSEL (
  id                       CHAR(64)           PRIMARY KEY,
  uuid                     VARCHAR(50)        NOT NULL UNIQUE,
  fnr                      VARCHAR(11)        NOT NULL,
  utsendings_dato          DATE               NOT NULL,
  opprettet                TIMESTAMP          NOT NULL,
  sist_endret              TIMESTAMP          NOT NULL
);
