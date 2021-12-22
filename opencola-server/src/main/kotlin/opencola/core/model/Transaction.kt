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
data class Transaction(val id: Long, val facts: List<Fact>){
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

