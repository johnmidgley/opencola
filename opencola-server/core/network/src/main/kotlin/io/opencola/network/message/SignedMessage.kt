package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray

import java.io.InputStream
import java.io.OutputStream

class SignedMessage(val from: Id, val message: ByteArray, val signature: ByteArray) {
    fun encode(): ByteArray {
        return Factory.encode(this)
    }

    companion object Factory: StreamSerializer<SignedMessage> {
        override fun encode(stream: OutputStream, value: SignedMessage) {
            Id.encode(stream, value.from)
            stream.writeByteArray(value.message)
            stream.writeByteArray(value.signature)
        }

        override fun decode(stream: InputStream): SignedMessage {
            return SignedMessage(Id.decode(stream), stream.readByteArray(), stream.readByteArray())
        }
    }
}