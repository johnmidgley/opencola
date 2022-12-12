package opencola.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*

data class UserSession(val username: String, val isLoggedIn: Boolean) : Principal

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }
}