package opencola.core.network

import opencola.core.model.Id

class Peer(val id: Id, val name: String, val host: String, val status: Status ) {
    enum class Status{
        Offline,
        Online
    }

    fun setStatus(status: Status): Peer {
        return Peer(id, name, host, status)
    }
}