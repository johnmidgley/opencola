package opencola.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import javax.crypto.SecretKey

fun Application.configureAuthentication(authenticationEnabled: Boolean, authSecretKey: SecretKey) {
    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                if (session.decodeAuthToken(authSecretKey)?.isValid() == true) {
                    session
                } else {
                    null
                }
            }

            challenge {
                if (authenticationEnabled) {
                    call.respondRedirect("/login")
                }
            }
        }
    }
}
