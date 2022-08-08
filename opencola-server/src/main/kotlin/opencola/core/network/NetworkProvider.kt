package opencola.core.network

import opencola.core.model.Authority
import opencola.core.storage.AddressBook
import java.net.URI

interface NetworkProvider {
    fun start()
    fun stop()

    // TODO: Needed?
    fun getAddress() : URI

    // TODO: Move address book into provider and remove these peer methods
    fun updatePeer(peer: Authority)
    fun removePeer(peer: Authority)

    // TODO: Should authority be Id?
    fun sendRequest(peer: Authority, request: Request): Response?
    fun setRequestHandler(handler: (Request) -> Response)
}

abstract class AbstractNetworkProvider(private val addressBook: AddressBook) : NetworkProvider {
    var handler: ((Request) -> Response)? = null

    override fun setRequestHandler(handler: (Request) -> Response) {
        this.handler = handler
    }
}