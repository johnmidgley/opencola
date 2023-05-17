package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.SignedTransaction
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Message as ProtoMessage

// Since transactions are dependent on a stable signature, and hence serialization, we don't re-serialize here, we just
// use the bytes computed when the transaction was persisted.
class PutTransactionsMessage(private val signedTransactions: Iterable<ByteArray>) :
    Message(messageType) {

    companion object : ProtoSerializable<PutTransactionsMessage, ProtoMessage.PutTransactionsMessage> {
        const val messageType = "PutTxns"

        override fun toProto(value: PutTransactionsMessage): ProtoMessage.PutTransactionsMessage {
            return ProtoMessage.PutTransactionsMessage.newBuilder()
                .addAllSignedTransactions(value.signedTransactions.map { ByteString.copyFrom(it) })
                .build()
        }

        override fun fromProto(value: ProtoMessage.PutTransactionsMessage): PutTransactionsMessage {
            return PutTransactionsMessage(value.signedTransactionsList.map { it.toByteArray() })
        }

        fun decodeProto(value: ByteArray) : PutTransactionsMessage {
            return fromProto(ProtoMessage.PutTransactionsMessage.parseFrom(value))
        }
    }

    override fun toProto(): ProtoMessage.PutTransactionsMessage {
        return toProto(this)
    }

    fun getSignedTransactions(): List<SignedTransaction> {
        return signedTransactions.map { SignedTransaction.decodeProto(it) }
    }
}