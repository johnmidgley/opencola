package opencola.core.model

import kotlinx.serialization.Serializable
import opencola.core.extensions.nullOrElse
import opencola.core.serialization.ByteArrayStreamCodec
import java.io.InputStream
import java.io.OutputStream

// TODO - Make MAXLONG?
const val UNCOMMITTED = -1L

// TODO: Use protobuf
// TODO: Intern ids and attributes
// TODO: Think about making this only usable from inside the entity store, so that transaction ids can be controlled
//  SubjectiveFact (add subject), and TransactionFact (add transaction id / epoch) - just one? Transaction fact? Subjective fact with epoch?
// TODO: Should Value be nullable? or should an empty value be considered null?
@Serializable
data class Fact(val authorityId: Id, val entityId: Id, val attribute: Attribute, val value: Value?, val operation: Operation, val transactionId: Long = UNCOMMITTED){

    override fun toString(): String {
        val decodedValue = value.nullOrElse { attribute.codec.decode(it.bytes) }
        return "{ authorityId: $authorityId entityId: $entityId attribute: $attribute value: $decodedValue operation: $operation transactionId: $transactionId"
    }

    fun updateTransactionId(transactionId: Long): Fact {
        return Fact(authorityId, entityId, attribute, value, operation, transactionId)
    }

    companion object Factory : ByteArrayStreamCodec<Fact> {
        override fun encode(stream: OutputStream, value: Fact) {
            Id.encode(stream, value.authorityId)
            Id.encode(stream, value.entityId)
            Attribute.encode(stream, value.attribute)
            Value.encode(stream, value.value)
            Operation.encode(stream, value.operation)
            writeLong(stream, value.transactionId)
        }

        override fun decode(stream: InputStream): Fact {
            return Fact(Id.decode(stream), Id.decode(stream), Attribute.decode(stream), Value.decode(stream), Operation.decode(stream), readLong(stream))
        }

    }
}