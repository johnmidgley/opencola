package io.opencola.serialization.capnproto

import org.capnproto.MessageBuilder
import org.capnproto.MessageReader
import org.capnproto.SerializePacked
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.channels.Channels

fun pack(messageBuilder: MessageBuilder) : ByteArray {
    val bytes = ByteArrayOutputStream().use {
        SerializePacked.writeToUnbuffered(Channels.newChannel(it), messageBuilder)
        it.toByteArray()
    }
    return bytes
}

fun unpack(bytes: ByteArray) : MessageReader {
    val messageReader = ByteArrayInputStream(bytes).use {
        SerializePacked.readFromUnbuffered(Channels.newChannel(it))
    }

    return messageReader
}