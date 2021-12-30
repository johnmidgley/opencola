package opencola.core.model

import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

// TODO - Make MAXLONG?
const val UNCOMMITTED = -1L

// TODO: Use protobuf
// TODO: Intern ids and attributes
// TODO: Think about making this only usable from inside the entity store, so that transaction ids can be controlled
// TODO: Make Datifact - pure data with all fields, Fact,
//  SubjectiveFact (add subject), and TransactionFact (add transaction id / epoch) - just one? Transaction fact? Subjective fact with epoch?
// TODO: Change add to operation enum
// TODO: Make ids ByteArray's at this level to be consistent
@Serializable
data class Fact(val authorityId: Id, val entityId: Id, val attribute: Attribute, val value: Value?, val add: Boolean, val transactionId: Long = UNCOMMITTED){

    override fun toString(): String {
        val decodedValue = attribute.codec.decode(value?.value)
        return "{ authorityId: $authorityId entityId: $entityId attribute: $attribute value: $decodedValue add: $add transactionId: $transactionId"
    }

    fun updateTransactionId(transactionId: Long): Fact {
        return Fact(authorityId, entityId, attribute, value, add, transactionId)
    }

    companion object Factory : ByteArrayStreamCodec<Fact> {
        override fun encode(stream: OutputStream, value: Fact): OutputStream {
            Id.encode(stream, value.authorityId)
            Id.encode(stream, value.entityId)
            TODO("Implement attribute encode")
            TODO("Implement value encode")
            TODO("Implement Operation enum and encode")
            stream.write(LongByteArrayCodec.encode(value.transactionId))
        }

        override fun decode(stream: InputStream): Fact {
            TODO("Not yet implemented")
        }

    }
}