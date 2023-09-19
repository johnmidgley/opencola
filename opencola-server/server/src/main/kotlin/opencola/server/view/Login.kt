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