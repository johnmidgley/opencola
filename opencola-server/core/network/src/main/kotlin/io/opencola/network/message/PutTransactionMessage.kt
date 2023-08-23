package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.network.protobuf.Network as Proto

// Since transactions are dependent on a stable signature, and hence serialization, we don't re-serialize here, we just
// use the bytes computed when the transaction was persisted.
class PutTransactionMessage private constructor(
    private val encodedSignedTransaction: ByteArray,
    key: MessageStorageKey,
    val lastTransactionId: Id? = null
) : Message(key) {
    constructor(signedTransaction: SignedTransaction, lastTransactionId: Id? = null) :
            this(signedTransaction.encodeProto(), MessageStorageKey.of(signedTransaction.transaction.id) , lastTransactionId)

    companion object : ProtoSerializable<PutTransactionMessage, Proto.PutTransactionMessage> {
        override fun toProto(value: PutTransactionMessage): Proto.PutTransactionMessage {
            return Proto.PutTransactionMessage.newBuilder()
                .setSignedTransaction(ByteString.copyFrom(value.encodedSignedTransaction))
                .also { builder ->
                    value.lastTransactionId?.let { builder.setCurrentTransactionId(it.toProto()) }
                }
                .build()
        }

        override fun fromProto(value: Proto.PutTransactionMessage): PutTransactionMessage {
            return PutTransactionMessage(
                value.signedTransaction.toByteArray(),
                MessageStorageKey.none,
                if (value.hasCurrentTransactionId()) Id.fromProto(value.currentTransactionId) else null
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.PutTransactionMessage {
            return Proto.PutTransactionMessage.parseFrom(bytes)
        }
    }

    fun getSignedTransaction(): SignedTransaction {
        return SignedTransaction.decodeProto(encodedSignedTransaction)
    }
}