package io.opencola.util

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Deflater.BEST_COMPRESSION
import java.util.zip.Inflater

fun compress(bytes: ByteArray?, level: Int = BEST_COMPRESSION, bufferSize: Int = 1024 * 16): ByteArray {
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

fun uncompress(buffer: ByteArray?, bufferSize: Int = 1024 * 16): ByteArray? {
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