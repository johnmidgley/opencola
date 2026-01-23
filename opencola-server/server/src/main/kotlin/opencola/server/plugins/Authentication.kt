/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
