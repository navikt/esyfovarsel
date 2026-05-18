package no.nav.syfo.auth

class FakeTokenConsumer : ITokenConsumer {
    override suspend fun getToken(scope: String): String = LOCAL_DUMMY_TOKEN

    override suspend fun getOnBehalfOfToken(
        scope: String,
        token: String,
    ): String = LOCAL_DUMMY_TOKEN

    companion object {
        private const val LOCAL_DUMMY_TOKEN = "local-dummy-token"
    }
}
