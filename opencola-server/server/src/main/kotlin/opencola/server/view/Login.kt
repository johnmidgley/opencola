package opencola.server.view

import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

suspend fun loginPage(call: ApplicationCall, message: String? = null) {
    call.respondHtml {
        head {
            link {
                rel = "stylesheet"
                href = "css/main.css"
            }
        }
        body {
            form(action = "/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                if (message != null) {
                    p {
                        +message
                    }
                }
                p {
                    +"Password:"
                    passwordInput(name = "password")
                }
                p {
                    submitInput { value = "Login" }
                }
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