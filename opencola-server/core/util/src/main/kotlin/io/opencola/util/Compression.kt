/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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

    override fun equals(other: Any?): Boolean {
        if (other !is CompressedBytes) return false
        return format == other.format && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    fun toProto(): Proto.CompressedBytes {
        return toProto(this)
    }
}

fun compress(format: CompressionFormat, bytes: ByteArray): CompressedBytes {
    val compressedBytes = when (format) {
        CompressionFormat.NONE -> CompressedBytes(format, bytes)
        CompressionFormat.DEFLATE -> CompressedBytes(format, deflate(bytes))
    }

    // Only compress if it really makes the data smaller
    return if(compressedBytes.bytes.size >= bytes.size)
        CompressedBytes(CompressionFormat.NONE, bytes)
    else
        compressedBytes
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