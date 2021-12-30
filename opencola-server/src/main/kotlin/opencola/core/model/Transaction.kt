package opencola.core.model

import opencola.core.security.sign
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.core.security.isValidSignature
import java.security.PrivateKey
import java.security.PublicKey

@Serializable
// TODO: data class?
// TODO: Change id to epoch
class Transaction{
    val authorityId: Id
    val id: Long
    private val transactionFacts: List<TransactionFact>
    fun getFacts(): List<Fact> {
        return transactionFacts.map { Fact(authorityId, it.entityId, it.attribute, it.value, it.operation, id) }
    }

    constructor(authorityId: Id, facts: List<Fact>, id: Long){
        this.authorityId = authorityId
        this.id = id
        validateFacts(facts)
        this.transactionFacts = facts.map{ TransactionFact.fromFact(it) }
    }

    private fun validateFacts(facts: List<Fact>){
        val authorities = facts.map { it.authorityId }.distinctBy { it.toString() }

        if (authorities.size != 1) {
            throw IllegalArgumentException("Attempt to construct transaction with facts from multiple authorities: ${authorities.joinToString()}")
        }
    }

    // TODO: This is common to a number of classes. Figure out how to make properly generic
    override fun toString(): String {
        return Json.encodeToString(this)
    }

    fun sign(privateKey: PrivateKey) : SignedTransaction {
        // This is probably not the right way to serialize. Likely should create a serializer / provider that can be
        // configured to serialize in an appropriate format.
        // TODO: Validate transaction
        return SignedTransaction(this, sign(privateKey, this.toString().toByteArray()))
    }

    @Serializable
    // TODO: Consider making value a Value with a clean string serializer (signature too). Maybe doesn't matter with protobuf, but nice for json
    data class TransactionFact(val entityId: Id, val attribute: Attribute, val value: Value?, val operation: Operation) {
         companion object Factory {
            fun fromFact(fact: Fact): TransactionFact {
                return TransactionFact(fact.entityId, fact.attribute, fact.value, fact.operation)
            }
        }
    }
}


// TODO: Make signature class?
// TODO: data class?
@Serializable
class SignedTransaction(val transaction: Transaction, val signature: ByteArray){
    // TODO: Fix signature serialization - right now json array vs. an encoded hex string
    fun isValidTransaction(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, transaction.toString().toByteArray(), signature)
    }
}

