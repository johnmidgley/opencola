package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.model.toId
import io.opencola.network.protobuf.Network as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.util.toProto

class MessagePayload(val from: Id, val message: Message) {
    companion object : ProtoSerializable<MessagePayload, Proto.Message > {
        private fun Proto.Message.Builder.setBody(body: Message) : Proto.Message.Builder {
            when(body) {
                is PingMessage -> this.setPing(PingMessage.toProto(body))
                is PongMessage -> this.setPong(PongMessage.toProto(body))
                is PutDataMessage -> this.setPutData(PutDataMessage.toProto(body))
                is GetDataMessage -> this.setGetData(GetDataMessage.toProto(body))
                is PutTransactionMessage -> this.setPutTransaction(PutTransactionMessage.toProto(body))
                is GetTransactionsMessage -> this.setGetTransactions(GetTransactionsMessage.toProto(body))
                else -> throw IllegalArgumentException("Unknown message type: ${body::class}")
            }

            return this
        }

        override fun toProto(value: MessagePayload): Proto.Message {
            return Proto.Message.newBuilder()
                .setId(value.message.id.toProto())
                .setFrom(value.from.toProto())
                .setBody(value.message)
                .build()
        }

        override fun fromProto(value: Proto.Message): MessagePayload {
            val body = when {
                value.hasPing() -> PingMessage.fromProto(value.ping)
                value.hasPong() -> PongMessage.fromProto(value.pong)
                value.hasPutData() -> PutDataMessage.fromProto(value.putData)
                value.hasGetData() -> GetDataMessage.fromProto(value.getData)
                value.hasPutTransaction() -> PutTransactionMessage.fromProto(value.putTransaction)
                value.hasGetTransactions() -> GetTransactionsMessage.fromProto(value.getTransactions)
                else -> throw IllegalArgumentException("Unknown message type: ${value.bodyCase}")
            }

            return MessagePayload(
                value.from.toId(),
                body
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Message {
            return Proto.Message.parseFrom(bytes)
        }
    }

    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }
}