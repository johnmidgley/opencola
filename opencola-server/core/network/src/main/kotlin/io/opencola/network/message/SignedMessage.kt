package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.security.Signator
import io.opencola.security.Signature
import io.opencola.serialization.*

import java.io.InputStream
import java.io.OutputStream

class SignedMessage(val from: Id, val message: UnsignedMessage, val signature: Signature) {
    constructor(from: Id, message: UnsignedMessage, signator: Signator) : this(
        from,
        message,
        signator.signBytes(from.toString(), message.payload)
    )

    fun encode(): ByteArray {
        return Factory.encode(this)
    }

    companion object Factory: StreamSerializer<SignedMessage> {
        override fun encode(stream: OutputStream, value: SignedMessage) {
            TODO("Replace with protobuf")
            Id.encode(stream, value.from)
            UnsignedMessage.encode(stream, value.message)
            Signature.encode(value.signature)
        }

        override fun decode(stream: InputStream): SignedMessage {
            return SignedMessage(
                Id.decode(stream),
                UnsignedMessage.decode(stream),
                Signature.decode(stream)
            )
        }
    }
}