/*
 * Copyright 2024 OpenCola
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

package io.opencola.relay.common.message.v2

import io.opencola.relay.common.protobuf.Relay
import io.opencola.security.PublicKeyProtoCodec
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey

class IdentityMessage(val publicKey: PublicKey) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<IdentityMessage, Proto.Identity> {
        override fun toProto(value: IdentityMessage): Proto.Identity {
            return Proto.Identity.newBuilder()
                .setPublicKey(PublicKeyProtoCodec.toProto(value.publicKey))
                .build()
        }

        override fun fromProto(value: Proto.Identity): IdentityMessage {
            return IdentityMessage(PublicKeyProtoCodec.fromProto(value.publicKey))
        }

        override fun parseProto(bytes: ByteArray): Relay.Identity {
            return Proto.Identity.parseFrom(bytes)
        }
    }
}