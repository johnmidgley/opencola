package io.opencola.util

import org.junit.Test
import kotlin.test.assertContentEquals

class CompressionTest {
    @Test
    fun testCompress() {
        val bytes = "Hello World!".toByteArray()
        val compressed = compress(bytes)
        val decompressed = uncompress(compressed)
        assertContentEquals(bytes, decompressed)
    }
}