package opencola.core.model

import kotlinx.serialization.Serializable
import opencola.core.extensions.nullOrElse
import opencola.core.serialization.StreamSerializer
import java.io.InputStream
import java.io.OutputStream

// TODO - Make MAX_LONG?
const val UNCOMMITTED = -1L

// TODO: Use protobuf
// TODO: Intern ids and attributes
// TODO: Think about making this only usable from inside the entity store, so that transaction ids can be controlled
//  SubjectiveFact (add subject), and TransactionFact (add transaction id / epoch) - just one? Transaction fact? Subjective fact with epoch?
@Serializable
data class Fact(val authorityId: Id, val entityId: Id, val attribute: Attribute, val value: Value, val operation: Operation, val transactionId: Id? = null){

    override fun toString(): String {
        val decodedValue = value.nullOrElse { attribute.codec.decode(it.bytes) }
        return "{ authorityId: $authorityId entityId: $entityId attribute: $attribute value: $decodedValue operation: $operation transactionId: $transactionId"
    }
        companion object Factory : StreamSerializer<Fact> {
        override fun encode(stream: OutputStream, value: Fact) {
            if(value.transactionId == null){
                throw IllegalArgumentException("Attempt to encode fact with no transaction id set")
            }

            Id.encode(stream, value.authorityId)
            Id.encode(stream, value.entityId)
            Attribute.encode(stream, value.attribute)
            Value.encode(stream, value.value)
            Operation.encode(stream, value.operation)
            Id.encode(stream, value.transactionId)
        }

        override fun decode(stream: InputStream): Fact {
            return Fact(Id.decode(stream), Id.decode(stream), Attribute.decode(stream), Value.decode(stream), Operation.decode(stream), Id.decode(stream))
        }

    }
}