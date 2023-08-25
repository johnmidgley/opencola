package io.opencola.relay.server

import io.ktor.server.netty.*
import io.opencola.relay.common.defaultOCRPort
import java.net.URI
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2

class RelayServer {
    private val webSocketRelayServerV1 = WebSocketRelayServerV1()
    private val webSocketRelayServerV2 = WebSocketRelayServerV2()
    private var nettyApplicationEngine: NettyApplicationEngine? = null

    val address = URI("ocr://0.0.0.0")

    fun start() {
        nettyApplicationEngine = startWebServer(defaultOCRPort, webSocketRelayServerV1, webSocketRelayServerV2)
    }

    suspend fun stop() {
        webSocketRelayServerV1.close()
        webSocketRelayServerV2.close()
        nettyApplicationEngine?.stop(1000, 1000)
    }
}