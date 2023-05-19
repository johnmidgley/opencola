package io.opencola.model

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.Model as ProtoModel
import io.opencola.security.Signature
import io.opencola.security.isValidSignature
import io.opencola.serialization.EncodingFormat
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

data class SignedTransaction(
    val encodingFormat: EncodingFormat,
    val encodedTransaction: ByteArray,
    val signature: Signature
) {
    val transaction: Transaction by lazy { decodeTransaction() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedTransaction

        if (!encodedTransaction.contentEquals(other.encodedTransaction)) return false
        return signature == other.signature
    }

    override fun hashCode(): Int {
        var result = encodedTransaction.contentHashCode()
        result = 31 * result + signature.hashCode()
        return result
    }

    fun isValidTransaction(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, encodedTransaction, signature)
    }

    private fun decodeTransaction(): Transaction {
        return when(encodingFormat) {
            EncodingFormat.OC -> Transaction.decode(encodedTransaction)
            EncodingFormat.PROTOBUF -> Transaction.decodeProto(encodedTransaction)
            else -> throw IllegalArgumentException("Unknown encoding format: $encodingFormat")
        }
    }

    fun encode(): ByteArray {
        return encode(this)
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object :
        StreamSerializer<SignedTransaction>,
        ProtoSerializable<SignedTransaction, ProtoModel.SignedTransaction> {
        override fun encode(stream: OutputStream, value: SignedTransaction) {
            require(value.encodingFormat == EncodingFormat.OC)
            val transaction = Transaction.decode(value.encodedTransaction)
            Transaction.encode(stream, transaction)
            value.signature.let {
                stream.writeByteArray(it.algorithm.toByteArray())
                stream.writeByteArray(it.bytes)
            }
        }

        override fun decode(stream: InputStream): SignedTransaction {
            val transactionBytes = Transaction.encode(Transaction.decode(stream))
            val signature = Signature(
                String(stream.readByteArray()),
                stream.readByteArray()
            )

            return SignedTransaction(EncodingFormat.OC, transactionBytes, signature)
        }

        override fun toProto(value: SignedTransaction): ProtoModel.SignedTransaction {
            require(value.encodingFormat == EncodingFormat.PROTOBUF)
            val builder = ProtoModel.SignedTransaction.newBuilder()
            builder.transaction = ByteString.copyFrom(value.encodedTransaction)
            builder.signature = value.signature.toProto()
            return builder.build()
        }

        override fun fromProto(value: ProtoModel.SignedTransaction): SignedTransaction {
            return SignedTransaction(
                EncodingFormat.PROTOBUF,
                value.transaction.toByteArray(),
                Signature.fromProto(value.signature)
            )
        }

        // TODO: toBytes can easily be moved to ProtobufSerializable. Not sure if fromBytes can be moved due to type erasure
        fun encodeProto(value: SignedTransaction): ByteArray {
            return toProto(value).toByteArray()
        }

        fun decodeProto(bytes: ByteArray): SignedTransaction {
            return fromProto(ProtoModel.SignedTransaction.parseFrom(bytes))
        }
    }
}