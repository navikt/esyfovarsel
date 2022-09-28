package no.nav.syfo.auth

import io.ktor.auth.*
import no.nav.syfo.AuthEnv

fun validBasicAuthCredentials(authEnv: AuthEnv, credentials: UserPasswordCredential): Boolean {
    val isValid = credentials.name == authEnv.serviceuserUsername && credentials.password == authEnv.serviceuserPassword
    if (!isValid) {
        log.error("System call attempting to authenticate with invalid credentials: ${credentials.name}/${credentials.password}")
    }
    return isValid
}
