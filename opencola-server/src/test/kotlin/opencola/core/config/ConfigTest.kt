package opencola.core.config

import opencola.core.TestApplication
import org.junit.Test
import kotlin.test.assertEquals

class ConfigTest {
    init{
        TestApplication.init()
    }

    @Test
    fun testLoadConfig(){
        assertEquals("test", Application.instance.config.env)
    }
}