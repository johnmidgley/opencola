package opencola.core.network

import kotlin.io.path.Path

interface Node {
    fun make()
    fun start() : Node
    fun stop()
    fun setNetworkToken(token: String)
}