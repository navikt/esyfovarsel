package no.nav.syfo

data class ApplicationState(
    var running: Boolean = false,
    var initialized: Boolean = false,
)

fun ApplicationState.shutdownApplication() {
    this.running = false
    this.initialized = false
}
