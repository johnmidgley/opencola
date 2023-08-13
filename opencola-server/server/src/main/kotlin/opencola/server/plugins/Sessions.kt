package opencola.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import opencola.server.AuthToken
import javax.crypto.SecretKey

data class UserSession(val authToken: String) : Principal {
    fun decodeAuthToken(authSecretKey: SecretKey): AuthToken? {
        return AuthToken.decode(authSecretKey, authToken)
    }
}

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 365
        }
    }
}