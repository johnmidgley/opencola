package io.opencola.network.message

import io.opencola.model.SignedTransaction
import io.opencola.serialization.protobuf.Message

// Since transactions are dependent on a stable signature, and hence serialization, we don't re-serialize here, we just
// use the bytes computed when the transaction was persisted. This is why this message doesn't implement
// ProtoSerializable, and looks different from other messages.
class PutTransactionsMessage(payload: ByteArray) : UnsignedMessage("PutTransactionsMessage", payload) {
    fun getTransactions(): List<SignedTransaction> {
        return Message.PutTransactionsMessage.parseFrom(payload).transactionsList.map {
            SignedTransaction.fromProto(it)
        }
    }
}