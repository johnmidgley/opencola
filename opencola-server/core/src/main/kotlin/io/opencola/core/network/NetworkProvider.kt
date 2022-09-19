package io.opencola.core.network

import io.opencola.core.model.Authority
import java.net.URI

interface NetworkProvider {
    fun start()
    fun stop()

    fun getScheme() : String
    fun validateAddress(address: URI)

    // If a peer URI changes with the same provider, it will result in removePeer(oldPeer) addPeer(newPeer)
    fun addPeer(peer: Authority)
    fun removePeer(peer: Authority)

    fun sendRequest(peer: Authority, request: Request): Response?
    fun setRequestHandler(handler: (Request) -> Response)
}

abstract class AbstractNetworkProvider : NetworkProvider {
    var handler: ((Request) -> Response)? = null

    override fun setRequestHandler(handler: (Request) -> Response) {
        this.handler = handler
    }
}