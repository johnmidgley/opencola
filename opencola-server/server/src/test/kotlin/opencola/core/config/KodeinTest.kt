package opencola.core.config

import opencola.core.TestApplication
import io.opencola.core.model.Authority
import org.junit.Test
import org.kodein.di.instance
import kotlin.test.assertNotNull

class KodeinTest {
    @Test
    fun testKodein(){
        val authority by TestApplication.instance.injector.instance<Authority>()
        assertNotNull(authority.authorityId)
    }
}