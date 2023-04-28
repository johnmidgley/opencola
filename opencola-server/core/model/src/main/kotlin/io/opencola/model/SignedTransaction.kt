package io.opencola.model

import com.google.protobuf.ByteString
import io.opencola.model.protobuf.Model as ProtoModel
import io.opencola.security.SIGNATURE_ALGO
import io.opencola.security.Signator
import io.opencola.security.isValidSignature
import io.opencola.serialization.ProtoSerializable
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

@Serializable
// TODO: Make Signature type that has algorithm and signature value - but need to wait until entity store
//  migration is complete - as old transactions depend on Json serialization of this class as is
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
        return signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object:
        StreamSerializer<SignedTransaction>,
        ProtoSerializable<SignedTransaction, ProtoModel.SignedTransaction> {
        override fun encode(stream: OutputStream, value: SignedTransaction) {
            Transaction.encode(stream, value.transaction)
            stream.writeByteArray(SIGNATURE_ALGO.toByteArray())
            stream.writeByteArray(value.signature)
        }

        override fun decode(stream: InputStream): SignedTransaction {
            return SignedTransaction(Transaction.decode(stream), String(stream.readByteArray()), stream.readByteArray())
        }

        fun fromTransaction(signator: Signator, transaction: Transaction): SignedTransaction {
            val signature = signator.signBytes(transaction.authorityId.toString(), Transaction.encode(transaction))
            return SignedTransaction(transaction, signature.algorithm, signature.bytes)
        }

        override fun toProto(value: SignedTransaction): ProtoModel.SignedTransaction {
            val builder = ProtoModel.SignedTransaction.newBuilder()
            builder.transaction = Transaction.toProto(value.transaction).toByteString()
            builder.signature = ProtoModel.Signature.newBuilder()
                .setAlgorithm(value.algorithm)
                .setBytes(ByteString.copyFrom(value.signature))
                .build()

            return builder.build()
        }

        override fun fromProto(value: ProtoModel.SignedTransaction): SignedTransaction {
            return SignedTransaction(
                Transaction.fromProto(ProtoModel.Transaction.parseFrom(value.transaction)),
                value.signature.algorithm,
                value.signature.bytes.toByteArray()
            )
        }

        // TODO: toBytes can easily be moved to ProtobufSerializable. Not sure if fromBytes can be moved due to type erasure
        fun toBytes(value: SignedTransaction): ByteArray {
            return toProto(value).toByteArray()
        }

        fun fromBytes(bytes: ByteArray): SignedTransaction {
            return fromProto(ProtoModel.SignedTransaction.parseFrom(bytes))
        }
    }
}