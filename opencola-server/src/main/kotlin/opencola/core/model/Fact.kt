package opencola.core.model

import kotlinx.serialization.Serializable
import java.net.URI

const val UNCOMMITTED = -1L

// TODO: Use protobuf
// TODO: Intern ids and attributes
// TODO: Think about making this only usable from inside the entity store, so that transaction ids can be controlled
// TODO: Make Datifact - pure data with all fields, Fact,
//  SubjectiveFact (add subject), and TransactionFact (add transaction id / epoch) - just one? Transaction fact? Subjective fact with epoch?
// TODO: Change add to operation enum
// TODO: Make ids ByteArray's at this level to be consistent
@Serializable
data class Fact(val authorityId: Id, val entityId: Id, val attribute: String, val value: ByteArray?, val add: Boolean, val transactionId: Long = UNCOMMITTED){

    override fun toString(): String {
        val decodedValue = Attributes.values().first { it.spec.uri == URI(attribute) }.spec.encoder.decode(value)
        return "{ authorityId: $authorityId entityId: $entityId attribute: $attribute value: $decodedValue add: $add transactionId: $transactionId"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Fact

        if (authorityId != other.authorityId) return false
        if (entityId != other.entityId) return false
        if (attribute != other.attribute) return false
        if (!value.contentEquals(other.value)) return false
        if (add != other.add) return false
        if (transactionId != other.transactionId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authorityId.hashCode()
        result = 31 * result + entityId.hashCode()
        result = 31 * result + attribute.hashCode()
        result = 31 * result + (value?.contentHashCode() ?: 0)
        result = 31 * result + add.hashCode()
        result = 31 * result + transactionId.hashCode()
        return result
    }
}