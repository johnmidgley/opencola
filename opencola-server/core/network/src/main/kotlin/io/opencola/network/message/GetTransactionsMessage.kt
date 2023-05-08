package io.opencola.network.message

import com.google.protobuf.GeneratedMessageV3
import io.opencola.model.Id
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Message as ProtoMessage

class GetTransactionsMessage(val mostRecentTransactionId: Id?, val maxTransactions: Int = 10) : Message(messageType) {
    // TODO: Companion objects should be moved to top of class
    companion object : ProtoSerializable<GetTransactionsMessage, ProtoMessage.GetTransactionsMessage> {
        val messageType = "GetTransactionsMessage"
        override fun toProto(value: GetTransactionsMessage): ProtoMessage.GetTransactionsMessage {
            return ProtoMessage.GetTransactionsMessage.newBuilder()
                .also {
                    if (value.mostRecentTransactionId != null)
                        it.setMostRecentTransactionId(value.mostRecentTransactionId.toProto())
                    it.maxTransactions = value.maxTransactions
                }
                .build()
        }

        override fun fromProto(value: ProtoMessage.GetTransactionsMessage): GetTransactionsMessage {
            return GetTransactionsMessage(Id.fromProto(value.mostRecentTransactionId), value.maxTransactions)
        }

        // TODO: Can these be part of ProtoSerializable or an abstract ProtoSerializable?
        fun fromPayload(payload: ByteArray): GetTransactionsMessage {
            return fromProto(ProtoMessage.GetTransactionsMessage.parseFrom(payload))
        }

        fun toPayload(value: GetTransactionsMessage): ByteArray {
            return toProto(value).toByteArray()
        }
    }

    override fun toProto(): GeneratedMessageV3 {
        return toProto(this)
    }
}