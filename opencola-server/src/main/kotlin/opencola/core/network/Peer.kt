package opencola.core.network

import opencola.core.model.Id

class Peer(val id: Id, val name: String, val host: String, val status: Status = Status.Unknown) {
    enum class Status{
        Unknown,
        Offline,
        Online
    }
}