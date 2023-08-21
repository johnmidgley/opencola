package io.opencola.relay.common.message.v1

import io.opencola.model.Id
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

// This class can be removed after V2 migration and replace by envelopeV2
class PayloadEnvelope(val to: PublicKey, val message: ByteArray) {
    override fun toString(): String {
        return "PayloadEnvelope(to=${Id.ofPublicKey(to)}, message=${message.size} bytes)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PayloadEnvelope) return false
        if (to != other.to) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int {
        var result = to.hashCode()
        result = 31 * result + message.contentHashCode()
        return result
    }

    fun encode(): ByteArray {
        return encode(this)
    }

    companion object : StreamSerializer<PayloadEnvelope> {

        // V1 encoding does not include the key
        override fun encode(stream: OutputStream, value: PayloadEnvelope) {
            stream.writeByteArray(value.to.encoded)
            stream.writeByteArray(value.message)
        }

        // V1 encoding does not include the key
        override fun decode(stream: InputStream): PayloadEnvelope {
            return PayloadEnvelope(
                publicKeyFromBytes(stream.readByteArray()),
                stream.readByteArray()
            )
        }
    }
}