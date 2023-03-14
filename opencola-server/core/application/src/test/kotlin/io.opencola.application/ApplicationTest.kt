package io.opencola.application

import org.junit.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRootPersonIsActive() {
        val rootPersona = TestApplication.instance.getPersonas().first()
        assertEquals(true, rootPersona.isActive)
    }
}