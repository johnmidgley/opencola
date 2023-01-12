package io.opencola.core.network

import io.opencola.core.model.Id
import io.opencola.core.security.Encryptor
import io.opencola.core.security.encrypt
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.PublicKey

class MessageEnvelope(val to: Id, val message: Message) {
    fun encode(encryptForPublicKey: PublicKey? = null) : ByteArray {
        val messageBytes = message.encode().let { msg -> encryptForPublicKey?.let { encrypt(it, msg) } ?: msg }

        return ByteArrayOutputStream().use {
            Id.encode(it, to)
            it.writeByteArray(messageBytes)
            it.toByteArray()
        }
    }

    companion object Factory {
        fun decode(envelopeBytes: ByteArray, encryptor: Encryptor? = null): MessageEnvelope {
            return ByteArrayInputStream(envelopeBytes).use {
                val id = Id.decode(it)
                val messageBytes = it.readByteArray().let { bytes -> encryptor?.decrypt(id, bytes) ?: bytes }
                MessageEnvelope(id, Message.decode(messageBytes))
            }
        }
    }
}

