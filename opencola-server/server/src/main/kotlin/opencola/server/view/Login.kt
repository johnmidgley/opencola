/*
 * Copyright 2024 OpenCola
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

package opencola.server.view

import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

suspend fun loginPage(call: ApplicationCall, message: String? = null) {
    call.respondHtml {
        head {
            link {
                rel = "stylesheet"
                href = "css/login.css"
            }
            script { src = "js/main.js" }
        }
        body {
            div {
                classes = setOf("login-container")
                img(src = "img/pull-tab.png", classes = "logo")
                form(action = "/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                    p {
                        +"Login to OpenCola"
                    }
                    p {
                        passwordInput(name = "password") {
                            placeholder = "Password"
                        }
                    }
                    if (message != null) {
                        p {
                            classes = setOf("error")
                            +message
                        }
                    }
                    p {
                        submitInput { value = "Login" }
                    }
                }
                // script { unsafe { raw("onSubmitHashFormFields(document.querySelector('form'), ['password'])") } }
            }
        }
    }
}

suspend fun loggedIn(call: ApplicationCall) {
    call.respondHtml {
        body {
            +"You are now logged in. This window will close momentarily."
            script {
                unsafe {
                    raw("window.close(); window.location.href = '/';")
                }
            }
        }
    }
}