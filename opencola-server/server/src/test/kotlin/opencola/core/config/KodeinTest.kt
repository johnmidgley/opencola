package opencola.core.config

import io.opencola.application.TestApplication
import io.opencola.storage.addressbook.AddressBook
import org.junit.Test

class KodeinTest {
    @Test
    fun testKodein(){
        TestApplication.instance.inject<AddressBook>()
    }
}