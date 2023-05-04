package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.security.Encryptor
import io.opencola.security.encrypt
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.PublicKey

class MessageEnvelope(val to: Id, val signedMessage: SignedMessage) {
    fun encode(encryptForPublicKey: PublicKey? = null) : ByteArray {
        val messageBytes = signedMessage.encode().let { msg -> encryptForPublicKey?.let { encrypt(it, msg) } ?: msg }

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
                val messageBytes = it.readByteArray().let { bytes -> encryptor?.decrypt(id.toString(), bytes) ?: bytes }
                MessageEnvelope(id, SignedMessage.decode(messageBytes))
            }
        }
    }
}

