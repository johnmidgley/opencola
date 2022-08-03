package opencola.core.network.providers.http

import opencola.core.config.ServerConfig
import opencola.core.model.Authority
import opencola.core.network.NetworkProvider
import opencola.core.network.Request
import opencola.core.network.Response
import java.net.URI

class HttpNetworkProvider(serverConfig: ServerConfig) : NetworkProvider {
    val serverAddress = URI("http://${serverConfig.host}:${serverConfig.port}")

    override fun start() {
        // Nothing to do
    }

    override fun stop() {
        // Nothing to do
    }

    override fun getAddress(): URI {
        return serverAddress
    }

    override fun updatePeer(peer: Authority) {
        // Nothing to do
    }

    override fun removePeer(peer: Authority) {
        // Nothing to do
    }

    // Caller (Network Node) should check if peer is active
    override fun sendRequest(peer: Authority, request: Request) : Response {
        TODO("Not yet implemented")
    }
}