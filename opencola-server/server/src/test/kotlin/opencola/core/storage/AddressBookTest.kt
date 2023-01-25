package opencola.core.storage

import opencola.core.TestApplication
import io.opencola.model.Authority
import io.opencola.model.Persona
import io.opencola.security.generateKeyPair
import io.opencola.storage.AddressBook
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

    private val updateHandler: (Authority?, Authority?) -> Unit = { previousAuthority, currentAuthority ->
        when(action) {
            null -> throw IllegalStateException("Missing action")
            Create -> { assertNull(previousAuthority); assertNotNull(currentAuthority) }
            Update -> {
                assertNotNull(previousAuthority)
                assertNotNull(currentAuthority)
                assertEquals("Test", previousAuthority.name)
                assertEquals("Test 2", currentAuthority.name)
            }
            Delete -> { assertNotNull(previousAuthority); assertNull(currentAuthority) }
        }


    }


    @Test
    fun testAddressBookUpdateHandler(){
        val addressBook = TestApplication.instance.inject<AddressBook>()
        val persona = addressBook.getAuthorities().filterIsInstance<Persona>().single()
        val peer = Authority(persona.entityId, generateKeyPair().public, URI("http://test"), "Test")

        addressBook.addUpdateHandler(updateHandler)
        action = Create
        addressBook.updateAuthority(peer)
        action = Update
        peer.name = "Test 2"
        addressBook.updateAuthority(peer)
        action = Delete
        addressBook.deleteAuthority(persona.entityId, peer.entityId)

        addressBook.removeUpdateHandler(updateHandler)
    }
}