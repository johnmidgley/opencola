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
import kotlin.io.path.Path

class ProcessNode(private val nodePath: Path, val name: String, val port: Int) : Node {
    private val logger = KotlinLogging.logger("TestNode")
    private val jsonHttpClient = JsonHttpClient()
    private val host = "http://0.0.0.0"
    private var process: Process? = null

    override fun make() {
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

    override fun start(): Node {
        logger.info { "Starting $name" }
        val process = "./start-node $name".startProcess(nodePath)!!.also { this.process == process }

        MultiStreamReader(listOf(Pair(name, process.inputStream), Pair(name, process.errorStream))).use { reader ->
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

    override fun stop() {
        if(process != null)
            process!!.destroy()
    }

    override fun setNetworkToken(token: String){
        val peersPath = "$host:$port/peers"
        val peersResult: PeersResult = jsonHttpClient.get(peersPath)
        val authorityId = peersResult.authorityId

        val authority = peersResult.results.first{ it.id == authorityId }
        val peer = Peer(authority.id, authority.name, authority.publicKey, authority.address, authority.imageUri, authority.isActive, token)
        jsonHttpClient.put(peersPath, peer)
    }

    companion object Factory {
        private val nodeDir = Path("../test")
        private const val basePort = 5750

        fun getNode(num: Int): ProcessNode {
            return ProcessNode(nodeDir, "node$num", basePort + num)
        }

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