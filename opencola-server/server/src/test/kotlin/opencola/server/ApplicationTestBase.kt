package opencola.server

import io.opencola.application.TestApplication
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.application.Application
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import opencola.server.plugins.UserSession
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureRouting
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.opencola.security.generateAesKey
import mu.KotlinLogging

abstract class ApplicationTestBase(name: String = "ApplicationTest") {
    protected val application = TestApplication.instance
    protected val logger = KotlinLogging.logger(name)

    protected inline fun <reified T> inject() : T {
        return application.inject()
    }

    protected fun configure(app: Application) {
        app.install(Authentication) {
            session<UserSession>("auth-session") {
                validate { session -> session }
            }
        }
        app.configureRouting(application, generateAesKey())
        app.configureContentNegotiation()
        app.install(Sessions) {
            cookie<UserSession>("user_session")
        }
    }

    protected fun getClient(builder: ApplicationTestBuilder): HttpClient {
        return builder.createClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }
}