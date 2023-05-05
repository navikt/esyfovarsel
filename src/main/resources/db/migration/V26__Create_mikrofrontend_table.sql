CREATE TABLE MIKROFRONTEND_SYNLIGHET (
  uuid              UUID            PRIMARY KEY,
  synlig_for        VARCHAR(11)     NOT NULL,
  tjeneste          VARCHAR(30)     NOT NULL,
  synlig_tom        DATE,
  opprettet         TIMESTAMP       NOT NULL,
  sist_endret       TIMESTAMP       NOT NULL
);
