package opencola.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.opencola.application.LoginConfig
import io.opencola.security.EncryptionParams

fun Application.configureAuthentication(loginConfig: LoginConfig, authEncryptionParams: EncryptionParams) {
    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                if (session.decodeAuthToken(authEncryptionParams)?.isValid() == true) {
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
