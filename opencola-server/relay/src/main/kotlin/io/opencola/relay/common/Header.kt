package io.opencola.relay.common

import io.opencola.core.security.publicKeyFromBytes
import io.opencola.core.serialization.*
import opencola.core.extensions.toByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.*

data class Header(val sessionId: UUID, val to: PublicKey, val messageType: MessageType) {
    constructor(to: PublicKey) : this(UUID.randomUUID(), to, MessageType.Deliver)

    companion object : StreamSerializer<Header> {
        override fun encode(stream: OutputStream, value: Header) {
            stream.writeByteArray(value.sessionId.toByteArray())
            stream.writeByteArray(value.to.encoded)
            stream.writeInt(value.messageType.ordinal)
        }

        override fun decode(stream: InputStream): Header {
            return Header(
                UUIDByteArrayCodecCodec.decode(stream.readByteArray()),
                publicKeyFromBytes(stream.readByteArray()),
                MessageType.values()[stream.readInt()]
            )
        }
    }
}