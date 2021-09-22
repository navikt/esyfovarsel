package no.nav.syfo.auth

data class WellKnown(
    var authorization_endpoint: String,
    var issuer: String,
    var jwks_uri: String,
    var token_endpoint: String
)
