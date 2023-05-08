package io.opencola.model

import io.opencola.serialization.protobuf.Model as ProtoModel
import io.opencola.security.Signature
import io.opencola.security.isValidSignature
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

data class SignedTransaction(val transaction: Transaction, val signature: Signature) {
    // The payload is the raw bytes that were signed. Needed to guarantee signature validity
    // This can be removed once the database is migrated to use protobuf, at which point
    // the constructor can just take the payload as a parameter
    var payload: ByteArray? = null
        private set

    fun isValidTransaction(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, Transaction.encode(transaction), signature)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedTransaction

        if (transaction != other.transaction) return false
        return signature == other.signature
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + signature.hashCode()
        return result
    }

    companion object:
        StreamSerializer<SignedTransaction>,
        ProtoSerializable<SignedTransaction, ProtoModel.SignedTransaction> {
        override fun encode(stream: OutputStream, value: SignedTransaction) {
            Transaction.encode(stream, value.transaction)
            value.signature.let {
                stream.writeByteArray(it.algorithm.toByteArray())
                stream.writeByteArray(it.bytes)
            }
        }

        override fun decode(stream: InputStream): SignedTransaction {
            val transaction = Transaction.decode(stream)
            val signature = Signature(
                String(stream.readByteArray()),
                stream.readByteArray()
            )

            return SignedTransaction(transaction, signature)
        }

        override fun toProto(value: SignedTransaction): ProtoModel.SignedTransaction {
            val builder = ProtoModel.SignedTransaction.newBuilder()
            builder.transaction = value.transaction.toProto().toByteString()
            builder.signature = value.signature.toProto()
            return builder.build()
        }

        override fun fromProto(value: ProtoModel.SignedTransaction): SignedTransaction {
            return SignedTransaction(
                Transaction.fromProto(ProtoModel.Transaction.parseFrom(value.transaction)),
                Signature.fromProto(value.signature)
            )
        }

        // TODO: toBytes can easily be moved to ProtobufSerializable. Not sure if fromBytes can be moved due to type erasure
        fun toBytes(value: SignedTransaction): ByteArray {
            return toProto(value).toByteArray().also { value.payload = it }
        }

        fun fromBytes(bytes: ByteArray): SignedTransaction {
            return fromProto(ProtoModel.SignedTransaction.parseFrom(bytes)).also { it.payload = bytes }
        }
    }
}