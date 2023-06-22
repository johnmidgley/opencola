package io.opencola.util

import com.google.protobuf.ByteString
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Deflater.BEST_COMPRESSION
import java.util.zip.Inflater
import io.opencola.util.protobuf.Util as Proto

enum class CompressionFormat {
    NONE,
    DEFLATE;

    companion object {
        fun fromProto(value: Proto.CompressionFormat): CompressionFormat {
            return when (value) {
                Proto.CompressionFormat.UNRECOGNIZED -> throw IllegalArgumentException("Unrecognized compression format")
                Proto.CompressionFormat.NONE -> NONE
                Proto.CompressionFormat.DEFLATE -> DEFLATE
            }
        }

        fun toProto(value: CompressionFormat) : Proto.CompressionFormat {
            return when (value) {
                NONE -> Proto.CompressionFormat.NONE
                DEFLATE -> Proto.CompressionFormat.DEFLATE
            }
        }
    }

    fun toProto(): Proto.CompressionFormat {
        return toProto(this)
    }
}

class CompressedBytes(val format: CompressionFormat, val bytes: ByteArray) {
    companion object {
        fun toProto(value: CompressedBytes): Proto.CompressedBytes {
            return Proto.CompressedBytes.newBuilder()
                .setFormat(value.format.toProto())
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()
        }

        fun fromProto(value: Proto.CompressedBytes): CompressedBytes {
            return CompressedBytes(
                CompressionFormat.fromProto(value.format),
                value.bytes.toByteArray()
            )
        }
    }

    fun toProto(): Proto.CompressedBytes {
        return toProto(this)
    }
}

fun compress(format: CompressionFormat, bytes: ByteArray): CompressedBytes {
    return when (format) {
        CompressionFormat.NONE -> CompressedBytes(format, bytes)
        CompressionFormat.DEFLATE -> CompressedBytes(format, deflate(bytes))
    }
}

fun uncompress(compressedBytes: CompressedBytes): ByteArray {
    return when (compressedBytes.format) {
        CompressionFormat.NONE -> compressedBytes.bytes
        CompressionFormat.DEFLATE -> inflate(compressedBytes.bytes)
    }
}

fun deflate(bytes: ByteArray?, level: Int = BEST_COMPRESSION, bufferSize: Int = 1024 * 16): ByteArray {
    val compressor = Deflater().also {
        it.setLevel(level)
        it.setInput(bytes)
        it.finish()
    }
    val buf = ByteArray(bufferSize)

    ByteArrayOutputStream().use {
        while (!compressor.finished()) {
            val count: Int = compressor.deflate(buf)
            it.write(buf, 0, count)
        }

        return it.toByteArray()
    }
}

fun inflate(buffer: ByteArray?, bufferSize: Int = 1024 * 16): ByteArray {
    val decompressor = Inflater().also { it.setInput(buffer) }
    val buf = ByteArray(bufferSize)

    ByteArrayOutputStream().use {
        while (!decompressor.finished()) {
            val count: Int = decompressor.inflate(buf)
            it.write(buf, 0, count)
        }

        return it.toByteArray()
    }
}