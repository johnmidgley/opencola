package opencola.core.storage

import opencola.core.TestApplication
import io.opencola.model.Authority
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
        val authority = TestApplication.instance.inject<Authority>()
        val addressBook = TestApplication.instance.inject<AddressBook>()
        val peer = Authority(authority.entityId, generateKeyPair().public, URI("http://test"), "Test")

        addressBook.addUpdateHandler(updateHandler)
        action = Create
        addressBook.updateAuthority(peer)
        action = Update
        peer.name = "Test 2"
        addressBook.updateAuthority(peer)
        action = Delete
        addressBook.deleteAuthority(peer.entityId)

        addressBook.removeUpdateHandler(updateHandler)
    }
}