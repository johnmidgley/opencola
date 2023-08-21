package io.opencola.network.message

import io.opencola.network.protobuf.Message.MessageType as MessageTypeProto

object MessageTypeProtoCodec {
    private val messageTypeToMessageTypeProtoMap =
        mapOf(
            MessageType.PING to MessageTypeProto.PING,
            MessageType.PONG to MessageTypeProto.PONG,
            MessageType.GET_TRANSACTIONS to MessageTypeProto.GET_TRANSACTIONS,
            MessageType.PUT_TRANSACTION to MessageTypeProto.PUT_TRANSACTION,
            MessageType.GET_DATA to MessageTypeProto.GET_DATA,
            MessageType.PUT_DATA to MessageTypeProto.PUT_DATA
        )

    private val messageTypeProtoToMessageTypeMap =
        messageTypeToMessageTypeProtoMap.map { (k, v) -> v to k }.toMap()

    fun toProto(messageType: MessageType): MessageTypeProto {
        return messageTypeToMessageTypeProtoMap[messageType] ?:
            throw IllegalArgumentException("Unknown MessageType: $messageType")
    }

    fun fromProto(messageTypeProto: MessageTypeProto): MessageType {
        return messageTypeProtoToMessageTypeMap[messageTypeProto] ?:
            throw IllegalArgumentException("Unknown MessageTypeProto: $messageTypeProto")
    }
}