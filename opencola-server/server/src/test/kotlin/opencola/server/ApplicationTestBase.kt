package opencola.server

import io.opencola.application.TestApplication
import io.ktor.server.application.*
import io.ktor.server.application.Application
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import opencola.server.plugins.UserSession
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.routing.configureRouting
import mu.KotlinLogging

abstract class ApplicationTestBase(name: String = "ApplicationTest") {
    protected val application = TestApplication.instance
    protected val logger = KotlinLogging.logger(name)

    protected inline fun <reified T> inject() : T {
        return application.inject()
    }

    protected fun configure(app: Application) {
        app.install(Authentication) {
            basic("auth-session") {
                // No auth needed for current tests, so ignore auth
                skipWhen { true }
            }
        }
        app.configureRouting(application)
        app.configureContentNegotiation()
        app.install(Sessions) {
            cookie<UserSession>("user_session")
        }
    }
}