package opencola.core

import opencola.core.config.App
import opencola.core.config.ConfigRoot
import org.junit.Test
import kotlin.test.assertEquals

class ConfigTest {
    init{
        ConfigRoot.load(App.path.resolve("opencola-test.yaml"))
    }

    @Test
    fun testLoadConfig(){
        assertEquals("test", ConfigRoot.config?.env)
    }
}