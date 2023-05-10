ALTER TABLE UTSENDT_VARSEL
    ADD COLUMN arbeidsgivernotifikasjon_merkelapp VARCHAR(50);

UPDATE UTSENDT_VARSEL
set arbeidsgivernotifikasjon_merkelapp = 'Oppfølging'
WHERE kanal = 'ARBEIDSGIVERNOTIFIKASJON'
  and type not in ('NL_DIALOGMOTE_INNKALT', 'NL_DIALOGMOTE_NYTT_TID_STED', 'NL_DIALOGMOTE_REFERAT', 'NL_DIALOGMOTE_AVLYST')
  and ferdigstilt_tidspunkt is null;
UPDATE UTSENDT_VARSEL
set arbeidsgivernotifikasjon_merkelapp = 'Dialogmøte'
WHERE kanal = 'ARBEIDSGIVERNOTIFIKASJON'
  and type in ('NL_DIALOGMOTE_INNKALT', 'NL_DIALOGMOTE_NYTT_TID_STED', 'NL_DIALOGMOTE_REFERAT', 'NL_DIALOGMOTE_AVLYST')
  and ferdigstilt_tidspunkt is null;
