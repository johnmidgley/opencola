package io.opencola.model

import com.google.protobuf.ByteString
import io.opencola.model.protobuf.Model as ProtoModel
import io.opencola.security.SIGNATURE_ALGO
import io.opencola.security.isValidSignature
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import kotlinx.serialization.Serializable
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
        if (algorithm != other.algorithm) return false
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

        fun packProto(signedTransaction: SignedTransaction): ByteArray {
            val builder = ProtoModel.SignedTransaction.newBuilder()
            builder.transaction = Transaction.packProto(signedTransaction.transaction)
            builder.signature = ProtoModel.Signature.newBuilder()
                .setAlgorithm(signedTransaction.algorithm)
                .setBytes(ByteString.copyFrom(signedTransaction.signature))
                .build()

            return builder.build().toByteArray()
        }

        fun unpackProto(bytes: ByteArray): SignedTransaction {
            val proto = ProtoModel.SignedTransaction.parseFrom(bytes)
            return SignedTransaction(
                Transaction.unpackProto(proto.transaction),
                proto.signature.algorithm,
                proto.signature.bytes.toByteArray()
            )
        }
    }
}