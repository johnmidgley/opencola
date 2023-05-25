package io.opencola.relay.common.connection

import io.ktor.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive

class WebSocketSessionWrapper(val webSocketSession: DefaultWebSocketSession) : SocketSession {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun isReady(): Boolean {
        return webSocketSession.isActive
                && !webSocketSession.incoming.isClosedForReceive
                && !webSocketSession.outgoing.isClosedForSend
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