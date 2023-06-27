package io.opencola.model

import io.opencola.model.protobuf.Model as Proto
import io.opencola.security.Signature
import io.opencola.security.SignatureAlgorithm
import io.opencola.security.isValidSignature
import io.opencola.serialization.EncodingFormat
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import io.opencola.util.CompressedBytes
import io.opencola.util.CompressionFormat
import io.opencola.util.uncompress
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

data class SignedTransaction(
    val encodingFormat: EncodingFormat,
    val compressedTransaction: CompressedBytes,
    val signature: Signature
) {
    val transaction: Transaction by lazy { decodeTransaction() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedTransaction

        if (compressedTransaction != other.compressedTransaction) return false
        return signature == other.signature
    }

    override fun hashCode(): Int {
        var result = compressedTransaction.format.hashCode()
        result = 31 * result + compressedTransaction.bytes.contentHashCode()
        result = 31 * result + signature.hashCode()
        return result
    }

    fun hasValidSignature(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, compressedTransaction.bytes, signature)
    }

    private fun decodeTransaction(): Transaction {
        val uncompressedBytes = uncompress(compressedTransaction)

        return when (encodingFormat) {
            EncodingFormat.OC -> Transaction.decode(uncompressedBytes)
            EncodingFormat.PROTOBUF -> Transaction.decodeProto(uncompressedBytes)
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
        ProtoSerializable<SignedTransaction, Proto.SignedTransaction> {
        override fun encode(stream: OutputStream, value: SignedTransaction) {
            require(value.encodingFormat == EncodingFormat.OC)
            val transaction = Transaction.decode(uncompress(value.compressedTransaction))
            Transaction.encode(stream, transaction)
            value.signature.let {
                stream.writeByteArray(it.algorithm.algorithmName.toByteArray())
                stream.writeByteArray(it.bytes)
            }
        }

        override fun decode(stream: InputStream): SignedTransaction {
            val transactionBytes = Transaction.encode(Transaction.decode(stream))
            val compressedBytes = CompressedBytes(CompressionFormat.NONE, transactionBytes)

            val signature = Signature(
                SignatureAlgorithm.fromAlgorithmName(String(stream.readByteArray())),
                stream.readByteArray()
            )

            return SignedTransaction(EncodingFormat.OC, compressedBytes, signature)
        }

        override fun toProto(value: SignedTransaction): Proto.SignedTransaction {
            require(value.encodingFormat == EncodingFormat.PROTOBUF)
            val builder = Proto.SignedTransaction.newBuilder()
            builder.compressedTransaction = value.compressedTransaction.toProto()
            builder.signature = value.signature.toProto()
            return builder.build()
        }

        override fun fromProto(value: Proto.SignedTransaction): SignedTransaction {
            return SignedTransaction(
                EncodingFormat.PROTOBUF,
                CompressedBytes.fromProto(value.compressedTransaction),
                Signature.fromProto(value.signature)
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.SignedTransaction {
            return Proto.SignedTransaction.parseFrom(bytes)
        }
    }
}