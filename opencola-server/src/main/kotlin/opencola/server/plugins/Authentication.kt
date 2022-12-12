package opencola.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.opencola.core.security.EncryptionParams

fun Application.configureAuthentication(authEncryptionParams: EncryptionParams) {
    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                if(session.decodeAuthToken(authEncryptionParams)?.isValid() == true) {
                    session
                } else {
                    null
                }
            }
            challenge {
                call.respondRedirect("/login")
            }
        }
    }
}