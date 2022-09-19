package opencola.core.network

import mu.KotlinLogging
import io.opencola.core.extensions.runCommand
import io.opencola.core.extensions.startProcess
import io.opencola.core.io.JsonHttpClient
import io.opencola.core.io.MultiStreamReader
import opencola.server.handlers.Peer
import opencola.server.handlers.PeersResult
import opencola.server.handlers.TokenRequest
import java.net.URI
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.io.path.Path

class ProcessNode(private val nodePath: Path, val name: String, val serverPort: Int, val ztPort: Int) : Node {
    private val logger = KotlinLogging.logger("ProcessNode")
    private val jsonHttpClient = JsonHttpClient()
    private val host = "http://0.0.0.0"
    private val serviceUri = URI("$host:$serverPort/")
    private var process: Process? = null

    override fun make() {
        logger.info { "Making $name" }
        "./make-node $name $serverPort $ztPort".runCommand(nodePath)
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
                jsonHttpClient.get<String>(serviceUri)
                isReady = true
            } catch (e: Exception){
                Thread.sleep(100)
            }
        }
    }

    override fun start(): Node {
        logger.info { "Starting $name" }
        val process = "./start-node $name".startProcess(nodePath)!!.also { this.process == process }

        blockOnProcessOutputUntil(process, name){ it.contains("MainReactor: NodeStarted") }
        blockUntilNodeReady()
        logger.info("Node $name is ready")
        return this
    }

    override fun stop() {
        if(process != null)
            process!!.destroy()
    }

    override fun getInviteToken(): String {
        return jsonHttpClient.get<TokenRequest>(serviceUri.resolve("/peers/token")).token
    }

    override fun postInviteToken(token: String): Peer {
        return jsonHttpClient.post(serviceUri.resolve("/peers/token"), TokenRequest(token))
    }

    override fun getPeers(): PeersResult {
        return jsonHttpClient.get(serviceUri.resolve("/peers"))
    }

    override fun updatePeer(peer: Peer) {
        jsonHttpClient.put<Unit>(serviceUri.resolve("/peers"), peer)
    }

    companion object Factory {
        private val logger = KotlinLogging.logger("ProcessNode")
        private val nodeDir = Path("../test")
        private const val baseServerPort = 5750
        private var built = false

        init {
            build()
        }

        fun getNode(num: Int): ProcessNode {
            return ProcessNode(nodeDir, "node-$num", baseServerPort + num, baseZtPort + num)
        }

        // TODO: Make extension methods on process?
        private fun readFromProcessUntil(process: Process, reader: MultiStreamReader, until: (String) -> Boolean){
            while (process.isAlive) {
                val line = reader.readLine()
                if (line != null) {
                    println(line)
                    if (until(line))
                        break
                } else
                    Thread.sleep(50)
            }
        }

        private fun echoProcessOutputInBackground(process: Process, reader: MultiStreamReader) {
            val executorService = Executors.newSingleThreadExecutor()
            executorService.execute{ readFromProcessUntil(process, reader) { false } }
        }

        private fun blockOnProcessOutputUntil(process: Process, prefix: String, until: (String) -> Boolean) {
            val reader = MultiStreamReader(listOf(Pair(prefix, process.inputStream), Pair(prefix, process.errorStream)))
            readFromProcessUntil(process, reader, until)
            echoProcessOutputInBackground(process, reader)
        }

        private fun build() {
            if(!built) {
                logger.info { "Building code" }
                "./build".runCommand(nodeDir, printOutput = true)
            }
            built = true
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