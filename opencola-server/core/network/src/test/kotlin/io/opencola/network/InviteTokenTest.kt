package io.opencola.network

import io.opencola.model.Id
import io.opencola.security.publicKeyFromBytes
import io.opencola.util.Base58
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class InviteTokenTest {
    private val testInviteTokenBase58 =
        "111ArKZN78zgFXKUsVKSEgArUYXZ31E8cy7sYA79tbzWa2xR1wKCR7hFe8jTKUCJNLcHSiZjM2vHkZerwxfGEYtwUFWKo5VBrT6jjtbM5QE4EiUhjQxXKtJBSnwbQabMwuDCYxTgvwxAMn2RQgVmtJ9dAsz8yQozKiPWZibvLcF1hySb8p7rc36Aiaqjw1An65qJaZJKTNQqrcU2SC873UVTAb7e15vojYtdCsjHMrAY9Et5AQD2d6D1XQTWZqVUMz6vT37cTLQbz9jtECeV65Qfue6fQXx4YE1hsbjvKTQQkhEGc3KeXLdwnpfbxaVsZ5JJjH4QyKHMugkg8hKCKXJaVpVyXAu4XsrK8smg4zqaPokpp8riZQewV1dZVSthPMKm24RuzxcbFUpgJ1TudhxP7"
    private val publicKeyBase58 = "aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTG6JMLDTnPCDLeT5NagPYnZXm97oX1TY1WytrLtkQKHV7MG4LC6wfdzmPMMWVyrSRz8SbQ5eSWsv7K3tvEMn7Ephm"
    private val publicKey = publicKeyFromBytes(Base58.decode(publicKeyBase58))

    @Test
    // If this test fails, likely something changed in the InviteToken encoding, which may be unexpected
    fun testInviteTokenStability() {
        val inviteToken = InviteToken.decodeBase58(testInviteTokenBase58)

        assertEquals(Id.decode("3wMUNDXsRD66vr3YhPSScPifozkTWhkTYVynECjX8e36"), inviteToken.authorityId)
        assertEquals("Test Identity", inviteToken.name)
        assertEquals(publicKey, inviteToken.publicKey)
        assertEquals(URI("ocr://relay.opencola.net"), inviteToken.address)
        assertEquals(URI(""), inviteToken.imageUri)
        assertEquals(inviteToken.tokenId, Id.decode("4PnuRAavAEiyWjuBAecEKZaNmtS9TRoP3wyowfJqBAgU"))
        assertEquals(1693526471, inviteToken.epochSecond)
        assertEquals(0, inviteToken.body.size)
    }
}