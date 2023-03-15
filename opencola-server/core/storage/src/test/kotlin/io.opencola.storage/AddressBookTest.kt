package io.opencola.storage

import io.opencola.application.TestApplication
import io.opencola.security.MockKeyStore
import org.junit.Test

class AddressBookTest {
    @Test
    fun testAddressBookInit() {
        val storagePath = TestApplication.getTmpDirectory(".storage")
        val keyStore = MockKeyStore()
        val addressBook0 = EntityStoreAddressBook(storagePath, keyStore)

        // Insert an inactive persona into the address book
        val persona0 = createPersona("Persona0", false)
        addressBook0.updateEntry(persona0)

        // Verify it was added and inactive
        val persona1 = addressBook0.getEntry(persona0.personaId, persona0.entityId)!!
        assert(!persona1.isActive)

        // Reinitialize and check that persona is now active
        val addressBook1 = EntityStoreAddressBook(storagePath, keyStore)
        val persona2 = addressBook1.getEntry(persona0.personaId, persona0.entityId)!!
        assert(persona2.isActive)
    }
}