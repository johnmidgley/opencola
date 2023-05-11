package io.opencola.storage

import io.opencola.application.TestApplication
import io.opencola.security.MockKeyStore
import io.opencola.storage.addressbook.EntityStoreAddressBook
import org.junit.Test
import java.net.URI

class AddressBookTest {
    @Test
    // This test is only needed for the EntityStoreAddressBook, as it was the only when that existed when
    // personas were created, and legacy authorities needed to be marked active.
    fun testAddressBookInit() {
        val storagePath = TestApplication.getTmpDirectory(".storage")
        val keyStore = MockKeyStore()
        val addressBook = EntityStoreAddressBook(storagePath, keyStore)

        // Insert an inactive persona into the address book
        val persona0 = addressBook.addPersona("Persona0", false)

        // Verify it was added and inactive
        addressBook.getEntry(persona0.personaId, persona0.entityId)!!.also { assert(!it.isActive) }

        // Reinitialize and check that persona is now active
        EntityStoreAddressBook(storagePath, keyStore)
            .getEntry(persona0.personaId, persona0.entityId)!!
            .also { assert(it.isActive) }
    }

    @Test
    // TODO: This is only testing the EntityStoreAddressBook. The synchronization should be abstracted and
    //  then this test could apply to any implementation.
    fun testPersonaSynchronization() {
        val addressBook = EntityStoreAddressBook(TestApplication.getTmpDirectory(".storage"), MockKeyStore())

        // Add 2 personas to address book
        val persona0 = addressBook.addPersona("Persona0")
        val persona1 = addressBook.addPersona("Persona1")

        val peer0 = addressBook.addPeer(persona0.entityId, "Name0")
        val peer1 = addressBook.addPeer(persona1.entityId, "Name1", publicKey = peer0.publicKey)

        // Verify that name is the same for both personas
        assert(peer1.equalsIgnoringPersona(addressBook.getEntry(persona0.entityId, peer0.entityId)))
        assert(peer1.equalsIgnoringPersona(addressBook.getEntry(persona1.entityId, peer0.entityId)))

        // Update all fields and verify change across personas
        val peer3 = peer0.set("Name3", URI("mock://peer3"), URI("mock://peer3/image"), !peer0.isActive)
        addressBook.updateEntry(peer3)
        assert(peer3.equalsIgnoringPersona(addressBook.getEntry(persona0.entityId, peer0.entityId)))
        assert(peer3.equalsIgnoringPersona(addressBook.getEntry(persona1.entityId, peer0.entityId)))
    }
}