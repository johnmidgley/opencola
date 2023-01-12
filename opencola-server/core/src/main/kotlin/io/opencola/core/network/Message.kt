package io.opencola.core.network

import io.opencola.core.model.Id
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray

import java.io.InputStream
import java.io.OutputStream

class Message(val from: Id, val body: ByteArray, val signature: ByteArray) {
    fun encode(): ByteArray {
        return Factory.encode(this)
    }

    companion object Factory: StreamSerializer<Message> {
        override fun encode(stream: OutputStream, value: Message) {
            Id.encode(stream, value.from)
            stream.writeByteArray(value.body)
            stream.writeByteArray(value.signature)
        }

        override fun decode(stream: InputStream): Message {
            return Message(Id.decode(stream), stream.readByteArray(), stream.readByteArray())
        }
    }
}