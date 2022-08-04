package opencola.server.handlers

import opencola.core.TestApplication
import opencola.core.model.Authority
import opencola.core.network.PeerRouter
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore
import org.apache.commons.math3.analysis.function.Add
import org.junit.Test
import org.kodein.di.instance

class FeedTest {
    @Test
    fun testFeedWithNoResults(){
        val app = TestApplication.instance
        getEntityResults(app.inject(), app.inject(), app.inject(), emptySet())
    }
}