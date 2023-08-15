package io.opencola.relay.common.message

import io.opencola.model.Id
//import io.opencola.security.toProto
import java.security.PublicKey

// TODO: Rename messageKey -> messageSecretKey
class Recipient(val publicKey: PublicKey, /* val messageSecretKey: EncryptedBytes */) {
    override fun toString(): String {
        return "Recipient(to=${Id.ofPublicKey(publicKey)}, messageSecretKey=ENCRYPTED)"
    }

    fun id() : Id {
        return Id.ofPublicKey(publicKey)
    }

//    fun toProto(): Proto.Recipient {
//        return toProto(this)
//    }
//
//    fun encodeProto(): ByteArray {
//        return encodeProto(this)
//    }
//
//    companion object : ProtoSerializable<Recipient, Proto.Recipient> {
//        override fun toProto(value: Recipient): Relay.Recipient {
//            return Proto.Recipient.newBuilder()
//                .setTo(value.to.toProto())
//                .setMessageKey(value.messageKey.toProto())
//                .build()
//        }
//
//        override fun fromProto(value: Proto.Recipient): Recipient {
//            return Recipient(
//                PublicKeyProtoCodec.fromProto(value.to),
//                EncryptedBytes.fromProto(value.messageKey)
//            )
//        }
//
//        override fun parseProto(bytes: ByteArray): Proto.Recipient {
//            return Proto.Recipient.parseFrom(bytes)
//        }
//    }
}
