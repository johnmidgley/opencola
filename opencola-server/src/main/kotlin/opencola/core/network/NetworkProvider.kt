package opencola.core.network

import opencola.core.model.Authority
import java.net.URI

interface NetworkProvider {
    fun start()
    fun stop()
    fun getAddress() : URI
    fun updatePeer(peer: Authority)
    fun removePeer(peer: Authority)
}