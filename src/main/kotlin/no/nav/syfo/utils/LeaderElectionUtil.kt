package no.nav.syfo.utils

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.append
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.getEnvVar
import no.nav.syfo.objectMapper
import org.slf4j.LoggerFactory

data class RunOnElection(
    val name: String,
    val runOnElection: () -> Unit,
    val oneShot: Boolean = true,
    var ranOnce: Boolean = false,
)

class LeaderElection(
    private val blocksToRun: List<RunOnElection>,
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.utils.LeaderElection")
    private var podIsLeader = false

    suspend fun checkIfPodIsLeader() {
        val electorPath = getEnvVar("ELECTOR_PATH")
        val electorPollUrl = "http://$electorPath"
        val leaderPod = getLeaderPod(electorPollUrl)
        val podHostname: String =
            withContext(Dispatchers.IO) {
                InetAddress.getLocalHost()
            }.hostName
        val isLeader = podHostname == leaderPod

        if (isLeader && !podIsLeader) {
            log.info("Pod elected to leader")
            blocksToRun.forEach { block ->
                if (!block.oneShot || !block.ranOnce) {
                    log.info("Running election code block: ${block.name}")
                    block.runOnElection()
                    block.ranOnce = true
                }
            }
        }

        podIsLeader = isLeader
    }

    private suspend fun getLeaderPod(path: String): String {
        val leaderJsonString = callElectorPath(path)
        return parseLeaderJson(leaderJsonString)
    }

    private suspend fun callElectorPath(path: String): String {
        val client = httpClient()
        val leaderResponse =
            client.get(path) {
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                }
            }
        return leaderResponse.body()
    }

    private fun parseLeaderJson(leaderJsonString: String): String {
        val leaderJson = objectMapper.readTree(leaderJsonString)
        return leaderJson["name"].toString().replace("\"", "")
    }
}
