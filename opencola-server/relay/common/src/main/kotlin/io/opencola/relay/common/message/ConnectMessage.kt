package io.opencola.relay.common.message

import com.google.protobuf.ByteString
import io.opencola.relay.common.protobuf.Relay
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey

class ConnectMessage(val publicKey: PublicKey) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<ConnectMessage, Proto.ConnectMessage> {
        override fun toProto(value: ConnectMessage): Proto.ConnectMessage {
            return Proto.ConnectMessage.newBuilder()
                .setPublicKey(ByteString.copyFrom(value.publicKey.encoded))
                .build()
        }

        override fun fromProto(value: Proto.ConnectMessage): ConnectMessage {
            return ConnectMessage(publicKeyFromBytes(value.publicKey.toByteArray()))
        }

        override fun parseProto(bytes: ByteArray): Relay.ConnectMessage {
            return Proto.ConnectMessage.parseFrom(bytes)
        }
    }
}