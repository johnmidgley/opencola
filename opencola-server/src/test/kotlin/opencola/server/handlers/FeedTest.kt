package opencola.server.handlers

import opencola.core.TestApplication
import opencola.core.model.Authority
import opencola.core.network.PeerRouter
import opencola.core.storage.EntityStore
import org.junit.Test
import org.kodein.di.instance

class FeedTest {
    @Test
    fun testFeedWithNoResults(){
        val injector = TestApplication.instance.injector

        val authority by injector.instance<Authority>()
        val entityStore by injector.instance<EntityStore>()
        val peerRouter by injector.instance<PeerRouter>()
        getEntityResults(authority, entityStore, peerRouter, emptySet())
    }
}