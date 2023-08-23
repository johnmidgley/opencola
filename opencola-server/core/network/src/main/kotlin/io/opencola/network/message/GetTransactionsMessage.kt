package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.network.protobuf.Network as Proto

class GetTransactionsMessage(val mostRecentTransactionId: Id?, val maxTransactions: Int = 10) :
    Message(MessageStorageKey.of("GET_TRANSACTIONS")) {

    companion object : ProtoSerializable<GetTransactionsMessage, Proto.GetTransactionsMessage> {
        override fun toProto(value: GetTransactionsMessage): Proto.GetTransactionsMessage {
            return Proto.GetTransactionsMessage.newBuilder()
                .also {
                    if (value.mostRecentTransactionId != null)
                        it.setCurrentTransactionId(value.mostRecentTransactionId.toProto())
                    it.maxTransactions = value.maxTransactions
                }
                .build()
        }

        override fun fromProto(value: Proto.GetTransactionsMessage): GetTransactionsMessage {
            val id = if (value.hasCurrentTransactionId()) Id.fromProto(value.currentTransactionId) else null
            return GetTransactionsMessage(id, value.maxTransactions)
        }

        override fun parseProto(bytes: ByteArray): Proto.GetTransactionsMessage {
            return Proto.GetTransactionsMessage.parseFrom(bytes)
        }
    }
}