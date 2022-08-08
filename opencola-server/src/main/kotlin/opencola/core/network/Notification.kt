package opencola.core.network

import kotlinx.serialization.Serializable
import opencola.core.model.Id
import opencola.core.serialization.StreamSerializer
import opencola.core.serialization.readInt
import opencola.core.serialization.writeInt
import java.io.InputStream
import java.io.OutputStream

enum class PeerEvent {
    Added,
    Online,
    NewTransaction
}

@Serializable
data class Notification(val peerId: Id, val event: PeerEvent)  {
    fun encode() : ByteArray {
        return Factory.encode(this)
    }

    companion object Factory : StreamSerializer<Notification> {
        override fun encode(stream: OutputStream, value: Notification) {
            Id.encode(stream, value.peerId)
            stream.writeInt(value.event.ordinal)
        }

        override fun decode(stream: InputStream): Notification {
            // TODO: Could throw exception
            return Notification(Id.decode(stream), PeerEvent.values()[stream.readInt()])
        }
    }
}