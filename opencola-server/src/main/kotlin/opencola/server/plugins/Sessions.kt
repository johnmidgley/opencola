package opencola.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.opencola.core.security.EncryptionParams
import opencola.server.AuthToken

data class UserSession(val authToken: String) : Principal {
    fun decodeAuthToken(encryptionParams: EncryptionParams) : AuthToken? {
        return AuthToken.decode(encryptionParams, authToken)
    }
}

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 365
        }
    }
}