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

import io.opencola.model.value.EmptyValue
import io.opencola.model.value.Value
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readLong
import io.opencola.serialization.writeLong
import java.io.InputStream
import java.io.OutputStream

// TODO: Use protobuf
// TODO: Intern ids and attributes
// TODO: Think about making this only usable from inside the entity store, so that transaction ids can be controlled
//  SubjectiveFact (add subject), and TransactionFact (add transaction id / epoch) - just one? Transaction fact? Subjective fact with epoch?
data class Fact(
    val authorityId: Id,
    val entityId: Id,
    val attribute: Attribute,
    val value: Value<Any>,
    val operation: Operation,
    val epochSecond: Long? = null,
    val transactionOrdinal: Long? = null,
) {
    override fun toString(): String {
        val unwrappedValue = if (value == EmptyValue) "EmptyValue" else attribute.valueWrapper.unwrap(value)
        return "{ authorityId: $authorityId entityId: $entityId attribute: ${attribute.uri} value: $unwrappedValue operation: $operation transactionOrdinal: $transactionOrdinal }"
    }

    // TODO: Remove?
    inline fun <reified T> unwrapValue(): T {
        return attribute.valueWrapper.unwrap(value) as T
    }

    companion object Factory : StreamSerializer<Fact> {
        override fun encode(stream: OutputStream, value: Fact) {
            if (value.transactionOrdinal == null) {
                throw IllegalArgumentException("Attempt to encode fact with no transaction id set")
            }
            if (value.epochSecond == null) {
                throw IllegalArgumentException("Attempt to encode fact with no epochSecond set")
            }

            Id.encode(stream, value.authorityId)
            Id.encode(stream, value.entityId)
            Attribute.encode(stream, value.attribute)
            value.attribute.valueWrapper.encode(stream, value.value)
            Operation.encode(stream, value.operation)
            stream.writeLong(value.epochSecond)
            stream.writeLong(value.transactionOrdinal)
        }

        override fun decode(stream: InputStream): Fact {
            val authorityId = Id.decode(stream)
            val entityId = Id.decode(stream)
            val attribute = Attribute.decode(stream)
            val value = attribute.valueWrapper.wrap(attribute.valueWrapper.decode(stream))
            val operation = Operation.decode(stream)
            val epochSecond = stream.readLong()
            val transactionOrdinal = stream.readLong()

            return Fact(authorityId, entityId, attribute, value, operation, epochSecond, transactionOrdinal)
        }

    }
}