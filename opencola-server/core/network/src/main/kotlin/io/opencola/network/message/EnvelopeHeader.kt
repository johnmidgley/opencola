package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.network.protobuf.Network as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

// TODO: MessageStorageKey should be moved to some place in core - shouldn't pull from relay
class EnvelopeHeader(val recipients: List<Recipient>, val messageStorageKey: MessageStorageKey) {
    constructor(recipient: Recipient, messageStorageKey: MessageStorageKey) : this(listOf(recipient), messageStorageKey)

    companion object : ProtoSerializable<EnvelopeHeader, Proto.EnvelopeHeader> {
        override fun toProto(value: EnvelopeHeader): Proto.EnvelopeHeader {
            return Proto.EnvelopeHeader.newBuilder()
                .addAllRecipients(value.recipients.map { it.toProto() })
                .also { value.messageStorageKey.encoded()?.let { key -> it.setStorageKey(ByteString.copyFrom(key)) }}
                .build()

        }

        override fun fromProto(value: Proto.EnvelopeHeader): EnvelopeHeader {
            return EnvelopeHeader(
                value.recipientsList.map { Recipient.fromProto(it) },
                if (value.storageKey.isEmpty) MessageStorageKey.none else MessageStorageKey.ofEncoded(value.storageKey.toByteArray())
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.EnvelopeHeader {
            return Proto.EnvelopeHeader.parseFrom(bytes)
        }
    }

    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }
}