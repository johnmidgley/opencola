package opencola.core

import opencola.core.config.Application
import opencola.core.config.ConfigRoot
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