package io.opencola.network.message

import com.google.protobuf.GeneratedMessageV3
import io.opencola.model.Id
import io.opencola.relay.common.message.MessageKey
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Message as Proto

class GetTransactionsMessage(val mostRecentTransactionId: Id?, val maxTransactions: Int = 10) :
    Message(messageType, MessageKey.of("GetTxns")) {

    companion object : ProtoSerializable<GetTransactionsMessage, Proto.GetTransactionsMessage> {
        val messageType = "GetTxns"
        override fun toProto(value: GetTransactionsMessage): Proto.GetTransactionsMessage {
            return Proto.GetTransactionsMessage.newBuilder()
                .also {
                    if (value.mostRecentTransactionId != null)
                        it.setMostRecentTransactionId(value.mostRecentTransactionId.toProto())
                    it.maxTransactions = value.maxTransactions
                }
                .build()
        }

        override fun fromProto(value: Proto.GetTransactionsMessage): GetTransactionsMessage {
            val id = if (value.hasMostRecentTransactionId()) Id.fromProto(value.mostRecentTransactionId) else null
            return GetTransactionsMessage(id, value.maxTransactions)
        }

        override fun parseProto(bytes: ByteArray): Proto.GetTransactionsMessage {
            return Proto.GetTransactionsMessage.parseFrom(bytes)
        }
    }

    override fun toProto(): GeneratedMessageV3 {
        return toProto(this)
    }
}