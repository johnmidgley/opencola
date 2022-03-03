package opencola.core.config

import opencola.core.TestApplication
import opencola.core.model.Authority
import org.junit.Test
import org.kodein.di.instance
import kotlin.test.assertNotNull

class KodeinTest {
    @Test
    fun testKodein(){
        TestApplication.init()
        val authority by Application.instance.injector.instance<Authority>()
        assertNotNull(authority.authorityId)
    }
}