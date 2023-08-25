package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.network.protobuf.Network as Proto

class GetTransactionsMessage(
    val senderCurrentTransactionId: Id?,
    val receiverCurrentTransactionId: Id?,
    val maxTransactions: Int = 10
) :
    Message(MessageStorageKey.of("GET_TRANSACTIONS")) {

    override fun toString(): String {
        return "GetTransactionsMessage(senderCurrentTransactionId=$senderCurrentTransactionId, receiverCurrentTransactionId=$receiverCurrentTransactionId, maxTransactions=$maxTransactions)"
    }

    companion object : ProtoSerializable<GetTransactionsMessage, Proto.GetTransactionsMessage> {
        override fun toProto(value: GetTransactionsMessage): Proto.GetTransactionsMessage {
            return Proto.GetTransactionsMessage.newBuilder()
                .also {
                    if (value.senderCurrentTransactionId != null)
                        it.setReceiverCurrentTransactionId(value.senderCurrentTransactionId.toProto())
                    if (value.receiverCurrentTransactionId != null)
                        it.setReceiverCurrentTransactionId(value.receiverCurrentTransactionId.toProto())
                    it.maxTransactions = value.maxTransactions
                }
                .build()
        }

        override fun fromProto(value: Proto.GetTransactionsMessage): GetTransactionsMessage {
            val senderCurrentTransactionsId =
                if (value.hasSenderCurrentTransactionId()) Id.fromProto(value.senderCurrentTransactionId) else null
            val receiverCurrentTransactionsId =
                if (value.hasReceiverCurrentTransactionId()) Id.fromProto(value.receiverCurrentTransactionId) else null
            return GetTransactionsMessage(senderCurrentTransactionsId, receiverCurrentTransactionsId, value.maxTransactions)
        }

        override fun parseProto(bytes: ByteArray): Proto.GetTransactionsMessage {
            return Proto.GetTransactionsMessage.parseFrom(bytes)
        }
    }
}