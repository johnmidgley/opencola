package io.opencola.security

import io.opencola.serialization.*
import io.opencola.util.Base58
import java.io.InputStream
import java.io.OutputStream
import java.security.PrivateKey

class Signature(val algorithm: String, val bytes: ByteArray) {
    override fun toString(): String {
        return Base58.encode(bytes)
    }

    companion object : StreamSerializer<Signature> {
        fun of(privateKey: PrivateKey, bytes: ByteArray): Signature {
            return sign(privateKey, bytes)
        }

        override fun encode(stream: OutputStream, value: Signature) {
            TODO("Replace with protobuf")
            stream.writeString(value.algorithm)
            stream.writeByteArray(value.bytes)
        }

        override fun decode(stream: InputStream): Signature {
            return Signature(stream.readString(), stream.readByteArray())
        }
    }
}