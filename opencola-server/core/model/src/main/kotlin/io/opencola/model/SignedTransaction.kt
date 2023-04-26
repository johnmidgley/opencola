package io.opencola.model

import io.opencola.model.capnp.Model
import io.opencola.security.SIGNATURE_ALGO
import io.opencola.security.isValidSignature
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import kotlinx.serialization.Serializable
import org.capnproto.MessageBuilder
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

@Serializable
// TODO: Make Signature type that has algorithm and signature value
data class SignedTransaction(val transaction: Transaction, val algorithm: String, val signature: ByteArray) {
    fun isValidTransaction(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, Transaction.encode(transaction), signature)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedTransaction

        if (transaction != other.transaction) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object Factory : StreamSerializer<SignedTransaction> {
        override fun encode(stream: OutputStream, value: SignedTransaction) {
            Transaction.encode(stream, value.transaction)
            stream.writeByteArray(SIGNATURE_ALGO.toByteArray())
            stream.writeByteArray(value.signature)
        }

        override fun decode(stream: InputStream): SignedTransaction {
            return SignedTransaction(Transaction.decode(stream), String(stream.readByteArray()), stream.readByteArray())
        }

        fun pack(signedTransaction: SignedTransaction): ByteArray {
            val messageBuilder = MessageBuilder()
            val message = messageBuilder.initRoot(Model.SignedTransaction.factory)
            Transaction.pack(signedTransaction.transaction, message.initTransaction())
            val signature = message.initSignature()
            signature.setAlgorithm(signedTransaction.algorithm)
            signature.setBytes(signedTransaction.signature)
            return io.opencola.serialization.capnproto.pack(messageBuilder)
        }

        fun unpack(bytes: ByteArray): SignedTransaction {
            val reader = io.opencola.serialization.capnproto.unpack(bytes).getRoot(Model.SignedTransaction.factory)

            return SignedTransaction(
                Transaction.unpack(reader.transaction),
                reader.signature.algorithm.toString(),
                reader.signature.bytes.toArray()
            )
        }
    }
}