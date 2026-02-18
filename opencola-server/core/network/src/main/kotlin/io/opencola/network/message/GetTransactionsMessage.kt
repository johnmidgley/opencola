/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
                        it.setSenderCurrentTransactionId(value.senderCurrentTransactionId.toProto())
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