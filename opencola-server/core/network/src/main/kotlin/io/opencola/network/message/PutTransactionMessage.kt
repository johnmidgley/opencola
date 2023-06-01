package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.relay.common.message.MessageKey
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Message as Proto

// Since transactions are dependent on a stable signature, and hence serialization, we don't re-serialize here, we just
// use the bytes computed when the transaction was persisted.
class PutTransactionMessage private constructor(
    private val encodedSignedTransaction: ByteArray,
    key: MessageKey,
    val lastTransactionId: Id? = null
) : Message(messageType, key) {
    constructor(signedTransaction: SignedTransaction, lastTransactionId: Id? = null) :
            this(signedTransaction.encodeProto(), MessageKey.of(signedTransaction.transaction.id) , lastTransactionId)

    companion object : ProtoSerializable<PutTransactionMessage, Proto.PutTransactionMessage> {
        const val messageType = "PutTxns"

        override fun toProto(value: PutTransactionMessage): Proto.PutTransactionMessage {
            return Proto.PutTransactionMessage.newBuilder()
                .setSignedTransaction(ByteString.copyFrom(value.encodedSignedTransaction))
                .also { builder ->
                    value.lastTransactionId?.let { builder.setLastTransactionId(it.toProto()) }
                }
                .build()
        }

        override fun fromProto(value: Proto.PutTransactionMessage): PutTransactionMessage {
            return PutTransactionMessage(
                value.signedTransaction.toByteArray(),
                MessageKey.none,
                if (value.hasLastTransactionId()) Id.fromProto(value.lastTransactionId) else null
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.PutTransactionMessage {
            return Proto.PutTransactionMessage.parseFrom(bytes)
        }
    }

    override fun toProto(): Proto.PutTransactionMessage {
        return toProto(this)
    }

    fun getSignedTransaction(): SignedTransaction {
        return SignedTransaction.decodeProto(encodedSignedTransaction)
    }
}