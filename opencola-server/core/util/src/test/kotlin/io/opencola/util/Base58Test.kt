package io.opencola.util

import java.util.*
import kotlin.test.*

class Base58Test {
    @Test
    fun testEncodeBase58() {
        val bytes = ByteArray(32).also { Random().nextBytes(it) }
        val decodedBytes = Base58.decode(Base58.encode(bytes))

        assertContentEquals(bytes, decodedBytes)
    }

    @Test
    fun testEncodeBase58Stability(){
        val reference = "EHfBRoVmFv8"
        val decodedString = String(Base58.decode(reference))

        assertEquals("OpenCola", decodedString)
    }
}