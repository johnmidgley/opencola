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

package io.opencola.model

import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.model.protobuf.Model as Proto
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readInt
import io.opencola.serialization.writeInt
import java.io.InputStream
import java.io.OutputStream

data class TransactionEntity(val entityId: Id, val facts: List<TransactionFact>) {
    companion object Factory :
        StreamSerializer<TransactionEntity>,
        ProtoSerializable<TransactionEntity, Proto.TransactionEntity> {
        override fun encode(stream: OutputStream, value: TransactionEntity) {
            Id.encode(stream, value.entityId)
            stream.writeInt(value.facts.size)
            for(fact in value.facts){
                TransactionFact.encode(stream, fact)
            }
        }

        override fun decode(stream: InputStream): TransactionEntity {
            return TransactionEntity(Id.decode(stream), stream.readInt().downTo(1).map { TransactionFact.decode(stream) } )
        }

        override fun toProto(value: TransactionEntity): Proto.TransactionEntity {
            return Proto.TransactionEntity.newBuilder()
                .setEntityId(Id.toProto(value.entityId))
                .addAllFacts(value.facts.map { TransactionFact.toProto(it) })
                .build()
        }

        override fun fromProto(value: Proto.TransactionEntity): TransactionEntity {
            return TransactionEntity(
                Id.fromProto(value.entityId),
                value.factsList.mapNotNull { TransactionFact.fromProto(it) }
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.TransactionEntity {
            return Proto.TransactionEntity.parseFrom(bytes)
        }
    }
}