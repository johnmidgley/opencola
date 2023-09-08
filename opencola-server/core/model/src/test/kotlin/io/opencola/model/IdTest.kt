package io.opencola.model

import io.opencola.security.publicKeyFromBytes
import io.opencola.util.Base58
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class IdTest {

    @Test
    fun testIdStability() {
        // IMPORTANT: If ids change, then data cannot be joined together properly. Here we verify that ids are stable
        val expectedDataId = "BP8SazUBe3GQnAiop6V2CayWtPaUeQUoK6KCzV7XUpJX"
        val dataId = Id.ofData("opencola".toByteArray())
        assertEquals(expectedDataId, dataId.toString(), "Id ofData changed")

        val expectedUriId = "9h8nkSFkPbn5uHqdQ2H71vVKKuoDBMsgDntUhkp4t2As"
        val uriId = Id.ofUri(URI("https://opencola.io"))
        assertEquals(expectedUriId, uriId.toString(), "Id ofUri changed")

        val base58PublicKey = "aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTKpzJShHg5iR3unen5PaUzxopULZ9tZ52aJpYpzoqEZd3PbegUsAz2AwrUzBnV7t57aekHg8L1AJ74QzuQ99QD4PX"
        val publicKey = publicKeyFromBytes(Base58.decode(base58PublicKey))
        val expectedPublicKeyId = "4Faj1SBs15VPzKTUU7d6qwTEtLDaKxZQX4GRmqCodmB6"
        val publicKeyId = Id.ofPublicKey(publicKey)
        assertEquals(expectedPublicKeyId, publicKeyId.toString(), "Id ofPublicKey changed")
    }
}