CREATE TABLE SYKETILFELLEBIT (
  uuid                     UUID               PRIMARY KEY,
  id                       VARCHAR(100)       NOT NULL,
  fnr                      VARCHAR(11)        NOT NULL,
  orgnummer                VARCHAR(9),
  opprettet                TIMESTAMP          NOT NULL,
  opprettet_opprinnelig    TIMESTAMP          NOT NULL,
  inntruffet               TIMESTAMP          NOT NULL,
  tags                     TEXT               NOT NULL,
  ressurs_id               VARCHAR(100)       NOT NULL,
  fom                      DATE               NOT NULL,
  tom                      DATE               NOT NULL,
  korrigert_soknad         TEXT
);
