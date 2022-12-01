CREATE TABLE FODSELSDATO (
  uuid                     UUID               PRIMARY KEY,
  fnr                      VARCHAR(11)        NOT NULL,
  fodselsdato              VARCHAR,
  opprettet_tidspunkt      TIMESTAMP          NOT NULL
);
