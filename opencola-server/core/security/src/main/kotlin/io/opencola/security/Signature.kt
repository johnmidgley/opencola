package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.serialization.*
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Security
import io.opencola.util.Base58
import java.io.InputStream
import java.io.OutputStream
import java.security.PrivateKey

class Signature(val algorithm: String, val bytes: ByteArray) {
    override fun toString(): String {
        return Base58.encode(bytes)
    }

    companion object : ProtoSerializable<Signature, io.opencola.serialization.protobuf.Security.Signature>
        /* StreamSerializer<Signature> */ {
        fun of(privateKey: PrivateKey, bytes: ByteArray): Signature {
            return sign(privateKey, bytes)
        }

        override fun toProto(value: Signature): Security.Signature {
            return Security.Signature.newBuilder()
                .setAlgorithm(value.algorithm)
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()
        }

        override fun fromProto(value: Security.Signature): Signature {
            return Signature(value.algorithm, value.bytes.toByteArray())
        }

//        override fun encode(stream: OutputStream, value: Signature) {
//            TODO("Replace with protobuf")
//            stream.writeString(value.algorithm)
//            stream.writeByteArray(value.bytes)
//        }
//
//        override fun decode(stream: InputStream): Signature {
//            return Signature(stream.readString(), stream.readByteArray())
//        }
    }
}