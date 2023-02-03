package opencola.server

import io.ktor.server.application.*
import io.ktor.server.application.Application
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import opencola.core.TestApplication
import opencola.server.plugins.UserSession
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureRouting

abstract class ApplicationTestBase {
    protected val application = TestApplication.instance
    protected val injector = TestApplication.instance.injector

    protected fun configure(app: Application) {
        app.install(Authentication) {
            session<UserSession>("auth-session") {
                validate { session -> session }
            }
        }
        app.configureRouting(application, AuthToken.encryptionParams)
        app.configureContentNegotiation()
        app.install(Sessions) {
            cookie<UserSession>("user_session")
        }
    }
}