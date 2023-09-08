package io.opencola.relay.common.connection

import io.ktor.websocket.*
import kotlinx.coroutines.isActive

class WebSocketSessionWrapper(val webSocketSession: DefaultWebSocketSession) : SocketSession {
    override suspend fun isReady(): Boolean {
        return webSocketSession.isActive
    }

    override suspend fun readSizedByteArray(): ByteArray {
        return (webSocketSession.incoming.receive() as Frame.Binary).data
    }

    override suspend fun writeSizedByteArray(byteArray: ByteArray) {
        webSocketSession.send(Frame.Binary(true, byteArray))
        webSocketSession.flush()
    }

    override suspend fun close() {
        webSocketSession.close()
    }
}