package opencola.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.opencola.application.LoginConfig
import javax.crypto.SecretKey

fun Application.configureAuthentication(loginConfig: LoginConfig, authSecretKey: SecretKey) {
    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                if (session.decodeAuthToken(authSecretKey)?.isValid() == true) {
                    session
                } else {
                    null
                }
            }

            if (loginConfig.authenticationRequired) {
                challenge {
                    call.respondRedirect("/login")
                }
            }
        }
    }
}
