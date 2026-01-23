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

package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.security.protobuf.Security as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey

object PublicKeyProtoCodec : ProtoSerializable<PublicKey, Proto.PublicKey> {
    override fun toProto(value: PublicKey): Proto.PublicKey {
        return Proto.PublicKey.newBuilder()
            .setEncoded(ByteString.copyFrom(value.encoded))
            .build()
    }

    override fun fromProto(value: Proto.PublicKey): PublicKey {
        return publicKeyFromBytes(value.encoded.toByteArray())
    }

    override fun parseProto(bytes: ByteArray): Proto.PublicKey {
        return Proto.PublicKey.parseFrom(bytes)
    }
}

fun PublicKey.toProto(): Proto.PublicKey = PublicKeyProtoCodec.toProto(this)
fun Proto.PublicKey.toPublicKey(): PublicKey = PublicKeyProtoCodec.fromProto(this)