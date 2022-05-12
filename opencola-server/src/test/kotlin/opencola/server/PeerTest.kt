package opencola.server

import io.ktor.application.*
import io.ktor.server.netty.*
import mu.KotlinLogging
import opencola.core.TestApplication
import opencola.core.config.Application

open class PeerTest {
    protected val logger = KotlinLogging.logger("PeerTransactionTest")
    private val basePortNumber: Int = 6000

    protected fun getApplications(nServers: Int): List<Application> {
        return opencola.core.config.getApplications(
            TestApplication.applicationPath,
            TestApplication.storagePath,
            TestApplication.config,
            basePortNumber,
            nServers
        )
    }

    protected fun startServer(engine: NettyApplicationEngine){
        // TODO: This is horrible. Figure out how to do this properly with suspend / coroutine / etc..
        var started = false
        engine.environment.monitor.subscribe(ApplicationStarted) { started = true }
        engine.start()
        while(!started){
            Thread.sleep(100)
        }
    }
}