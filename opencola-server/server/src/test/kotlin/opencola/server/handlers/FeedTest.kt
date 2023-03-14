package opencola.server.handlers

import io.opencola.application.TestApplication
import org.junit.Test

class FeedTest {
    @Test
    fun testFeedWithNoResults(){
        val app = TestApplication.instance
        handleGetFeed(setOf(app.getPersonas().single().personaId), app.inject(), app.inject(), app.inject(), "")
    }
}