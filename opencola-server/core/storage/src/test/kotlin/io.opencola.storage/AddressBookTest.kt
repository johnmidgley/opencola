package io.opencola.storage

import io.opencola.application.TestApplication
import io.opencola.model.Id
import io.opencola.security.JavaKeyStore
import io.opencola.security.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.security.generateKeyPair
import io.opencola.storage.AddressBookTest.Action.*
import io.opencola.storage.addressbook.*
import io.opencola.storage.addressbook.EntityStoreAddressBook.Version
import org.junit.Test
import java.net.URI
import kotlin.test.*

// TODO: More general routine
fun generatePersona(name: String): PersonaAddressBookEntry {
    val personKeyPair = generateKeyPair()
    val personaId = Id.ofPublicKey(personKeyPair.public)
    return PersonaAddressBookEntry(
        personaId,
        personaId,
        name,
        personKeyPair.public,
        URI("http://localhost:6543"),
        null,
        false,
        personKeyPair
    )
}

fun generatePeer(personaId: Id, name: String): AddressBookEntry {
    val personKeyPair = generateKeyPair()
    val peerId = Id.ofPublicKey(personKeyPair.public)
    return AddressBookEntry(
        personaId,
        peerId,
        name,
        personKeyPair.public,
        URI("http://localhost:6543"),
        null,
        false,
    )
}

fun equalsOtherThanPersonaId(source: AddressBookEntry, target: AddressBookEntry): Boolean {
    if(source.entityId != target.entityId) return false
    if(source.name != target.name) return false
    if(source.publicKey != target.publicKey) return false
    if(source.address != target.address) return false
    if(source.imageUri != target.imageUri) return false
    if(source.isActive != target.isActive) return false
    return true
}

fun getFreshKeyStore() = JavaKeyStore(TestApplication.getTmpFilePath("keystore.pks"), "password")
fun getFreshAddressBook(keyStore: KeyStore = getFreshKeyStore()) =
    EntityStoreAddressBook(Version.V2, AddressBookConfig(), TestApplication.getTmpDirectory("addressbook"), keyStore)

class AddressBookTest {
    @Test
    // This test is only needed for the EntityStoreAddressBook, as it was the only when that existed when
    // personas were created, and legacy authorities needed to be marked active.
    fun testAddressBookInit() {
        val storagePath = TestApplication.getTmpDirectory(".storage")
        val keyStore = MockKeyStore()
        val addressBook = EntityStoreAddressBook(Version.V2, AddressBookConfig(), storagePath, keyStore)

        // Insert an inactive persona into the address book
        val persona0 = addressBook.addPersona("Persona0", false)

        // Verify it was added and inactive
        addressBook.getEntry(persona0.personaId, persona0.entityId)!!.also { assert(!it.isActive) }

        // Reinitialize and check that persona is now active
        EntityStoreAddressBook(Version.V2, AddressBookConfig(), storagePath, keyStore)
            .getEntry(persona0.personaId, persona0.entityId)!!
            .also { assert(it.isActive) }
    }

    @Test
    // TODO: This is only testing the EntityStoreAddressBook. The synchronization should be abstracted and
    //  then this test could apply to any implementation.
    fun testPersonaSynchronization() {
        val addressBook = EntityStoreAddressBook(Version.V2, AddressBookConfig(), TestApplication.getTmpDirectory(".storage"), MockKeyStore())

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

    @Test
    fun testDeletePersona() {
        val keyStore = getFreshKeyStore()
        val addressBook = getFreshAddressBook(keyStore)
        assertEquals(0, keyStore.getAliases().size)

        // Create a persona
        val persona0 = generatePersona("persona0").also { addressBook.updateEntry(it) }
        val persona1 = generatePersona("persona1").also { addressBook.updateEntry(it) }

        val aliases = keyStore.getAliases()
        assertEquals(2, aliases.size)
        assertContains(aliases, persona0.personaId.toString())
        assertContains(aliases, persona1.personaId.toString())

        addressBook.deleteEntry(persona0.personaId, persona0.entityId)
        assertEquals(1, keyStore.getAliases().size)

        // Shouldn't be able to delete the only persona in an address book
        assertFails { addressBook.deleteEntry(persona1.personaId, persona1.entityId) }
    }

    @Test
    fun testMultiplePersonasConnectedToSamePeer() {
        val addressBook = getFreshAddressBook()

        // Create a persona
        val persona0 = generatePersona("Persona 0")
        addressBook.updateEntry(persona0)

        // Add a peer to the persona
        val peer0 = generatePeer(persona0.entityId, "Peer")
        addressBook.updateEntry(peer0)
        assertEquals(peer0, addressBook.getEntries().single { it !is PersonaAddressBookEntry })

        // Create a second persona
        val persona1 = generatePersona("Persona 1")
        addressBook.updateEntry(persona1)

        // Add the "same" peer to the second persona
        val peer1 = AddressBookEntry(
            persona1.personaId,
            peer0.entityId,
            peer0.name,
            peer0.publicKey,
            peer0.address,
            peer0.imageUri,
            peer0.isActive
        )
        addressBook.updateEntry(peer1)

        // Check that peers are the same
        addressBook.getEntries().filter { it !is PersonaAddressBookEntry }.forEach {
            assertTrue(equalsOtherThanPersonaId(peer0, it))
        }

        // Update one of the peers
        val peer1Updated = AddressBookEntry(
            persona1.personaId,
            peer0.entityId,
            "Name Changed",
            peer0.publicKey,
            URI("http://test"),
            URI("http://test"),
            !peer0.isActive
        )
        addressBook.updateEntry(peer1Updated)

        // Check that all peer entries for the same peer are updated (forced consistent)
        val peerEntries = addressBook.getEntries().filter { it !is PersonaAddressBookEntry }
        assertEquals(2, peerEntries.size)
        peerEntries.forEach {
            assertTrue(equalsOtherThanPersonaId(peer1Updated, it))
        }

        // While we have this set up, delete one of the personas
        addressBook.deleteEntry(persona0.personaId, persona0.entityId)

        // Check that the peer of the persona is deleted
        addressBook.getEntry(persona0.personaId, peer0.entityId)?.let {
            fail("Peer entry for persona 0 was not deleted")
        }
    }
}