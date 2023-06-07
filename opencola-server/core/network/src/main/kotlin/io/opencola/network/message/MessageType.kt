package io.opencola.network.message

enum class MessageType {
    PING,
    PONG,
    GET_TRANSACTIONS,
    PUT_TRANSACTION,
    GET_DATA,
    PUT_DATA,
}