package no.nav.syfo.exceptions

import java.io.IOException

class JournalpostDistribusjonException(
    message: String,
    val uuid: String? = null,
    val journalpostId: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class JournalpostDistribusjonGoneException(
    message: String,
    val uuid: String? = null,
    val journalpostId: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class JournalpostNetworkException(
    message: String,
    val uuid: String,
    val journalpostId: String,
    cause: Throwable,
) : IOException(message, cause)
