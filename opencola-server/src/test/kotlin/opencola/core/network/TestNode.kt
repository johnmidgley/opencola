package opencola.core.network

import mu.KotlinLogging
import opencola.core.extensions.runCommand
import opencola.core.extensions.startProcess
import opencola.core.io.JsonHttpClient
import opencola.core.io.MultiStreamReader
import opencola.server.handlers.Peer
import opencola.server.handlers.PeersResult
import java.nio.file.Path
import java.time.Instant

class TestNode(private val nodePath: Path, val name: String, val port: Int) {
    private val logger = KotlinLogging.logger("TestNode")
    private val jsonHttpClient = JsonHttpClient()
    private val host = "http://0.0.0.0"
    private var process: Process? = null

    fun make() {
        logger.info { "Making $name" }
        "./make-node $name $port".runCommand(nodePath)
    }

    private fun blockUntilNodeReady(){
        val startTime = Instant.now().epochSecond
        var isReady = false
        logger.info("Waiting until node is ready")

        while(!isReady){
            if (Instant.now().epochSecond - startTime > 10){
                throw RuntimeException("Node start timeout")
            }

            try {
                jsonHttpClient.get<String>("$host:$port")
                isReady = true
            } catch (e: Exception){
                Thread.sleep(100)
            }
        }
    }

    fun start(): TestNode {
        logger.info { "Starting $name" }
        val process = "./start-node $name".startProcess(nodePath)!!.also { this.process == process }

        MultiStreamReader(listOf(Pair("STD", process.inputStream), Pair("ERR", process.errorStream))).use { reader ->
            while (process.isAlive) {
                val line = reader.readLine()
                if (line != null) {
                    println(line)
                    if (line.contains("MainReactor: NodeStarted"))
                        break
                } else
                    Thread.sleep(50)
            }
        }

        blockUntilNodeReady()
        logger.info("Node $name is ready")
        return this
    }

    fun stop() {
        if(process != null)
            process!!.destroy()
    }

    private fun setNetworkToken(peer: Peer, networkToken: String): Peer {
        return Peer(peer.id, peer.name, peer.publicKey, peer.address, peer.imageUri, peer.isActive, networkToken)
    }

    private fun setNetworkToken(token: String){
        val peersPath = "$host:$port/peers"
        val peersResult: PeersResult = jsonHttpClient.get(peersPath)
        val authorityId = peersResult.authorityId

        val authority = peersResult.results.first{ it.id == authorityId }
        val peer = setNetworkToken(authority, token)
        jsonHttpClient.put(peersPath, peer)
    }

    companion object Factory {
        fun stopAllNodes(){
            "ps -eaf"
                .runCommand()
                .filter { it.contains("../../install/opencola/server") }
                .map { line -> line.split(" ").filter { it.isNotBlank() }[1] }
                .forEach{
                    "kill $it".runCommand()
                }
        }
    }
}