package opencola.core.config

import io.opencola.application.TestApplication
import org.junit.Test
import kotlin.test.assertEquals

class ConfigTest {
    @Test
    fun testLoadConfig(){
        assertEquals("test", TestApplication.instance.config.name)
    }
}