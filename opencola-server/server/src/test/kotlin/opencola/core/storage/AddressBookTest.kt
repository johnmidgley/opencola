package opencola.core.storage

import opencola.core.TestApplication
import io.opencola.model.Id
import io.opencola.security.generateKeyPair
import io.opencola.storage.AddressBook
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
import opencola.core.storage.AddressBookTest.Action.*
import java.net.URI
import kotlin.test.*

class AddressBookTest {
    enum class Action {
        Create,
        Update,
        Delete
    }

    private var action: Action? = null

    private val updateHandler: (AddressBookEntry?, AddressBookEntry?) -> Unit = { previousEntry, currentEntry ->
        when(action) {
            null -> throw IllegalStateException("Missing action")
            Create -> { assertNull(previousEntry); assertNotNull(currentEntry) }
            Update -> {
                assertNotNull(previousEntry)
                assertNotNull(currentEntry)
                assertEquals("Test", previousEntry.name)
                assertEquals("Test 2", currentEntry.name)
            }
            Delete -> { assertNotNull(previousEntry); assertNull(currentEntry) }
        }


    }


    @Test
    fun testAddressBookCRUDL(){
        val addressBook = TestApplication.instance.inject<AddressBook>()
        val persona = addressBook.getEntries().filterIsInstance<PersonaAddressBookEntry>().single()
        val peerKeyPair = generateKeyPair()
        val peer = AddressBookEntry(persona.entityId, Id.ofPublicKey(peerKeyPair.public), "Test", peerKeyPair.public, URI("http://test"), null, true)

        addressBook.addUpdateHandler(updateHandler)

        action = Create
        addressBook.updateEntry(peer)
        assertEquals(peer, addressBook.getEntry(peer.personaId, peer.entityId))

        action = Update
        val updatedPeer = AddressBookEntry(
            persona.entityId,
            Id.ofPublicKey(peerKeyPair.public),
            "Test 2",
            peerKeyPair.public,
            URI("http://test"),
            null,
            true
        )
        addressBook.updateEntry(updatedPeer)
        assertEquals(updatedPeer, addressBook.getEntry(peer.personaId, peer.entityId))
        assertContains(addressBook.getEntries(), updatedPeer)

        action = Delete
        addressBook.deleteEntry(peer.personaId, peer.entityId)
        assertNull(addressBook.getEntry(peer.personaId, peer.entityId))

        addressBook.removeUpdateHandler(updateHandler)
    }
}