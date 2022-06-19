package opencola.core.network

import opencola.server.handlers.PeersResult
import kotlin.io.path.Path

interface Node {
    fun make()
    fun start() : Node
    fun stop()
    fun setNetworkToken(token: String)
    fun getInviteToken() : String
    fun getPeers() : PeersResult
}