package io.opencola.util

import org.junit.Test
import kotlin.test.assertContentEquals

class CompressionTest {
    @Test
    fun testCompress() {
        val bytes = "Hello World!".toByteArray()
        val compressed = deflate(bytes)
        val decompressed = inflate(compressed)
        assertContentEquals(bytes, decompressed)
    }
}