package opencola.core.config

import io.opencola.test.TestApplication
import io.opencola.storage.AddressBook
import org.junit.Test

class KodeinTest {
    @Test
    fun testKodein(){
        TestApplication.instance.inject<AddressBook>()
    }
}