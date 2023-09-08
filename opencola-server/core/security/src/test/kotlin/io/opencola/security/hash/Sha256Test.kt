package io.opencola.security.hash

import org.junit.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun testSha256HashHexStability() {
        // IMPORTANT: Hex hashes are used by the search index to identify document / entity pairs in AbstractSearchIndex.
        // Changes to the hash will cause document ids to change, which can affect the search index
        val stableHash = "de32088425fb976a147c083753c1002f72ed85389e759f1f6ff0d3425dbe0657"
        assertEquals(stableHash, Sha256Hash.ofString("OpenCola").toHexString(), "Sha256Hash toHexString changed")
    }
}