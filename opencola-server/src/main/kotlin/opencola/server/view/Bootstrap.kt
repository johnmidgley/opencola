package opencola.server.view

import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

suspend fun startupForm(call: ApplicationCall, username: String, message: String? = null) {
    call.respondHtml {
        body {
            form(action = "/", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                if(message != null) {
                    p {
                        +message
                    }
                }
                p {
                    +"Username:"
                    textInput {
                        name = "username"
                        value = username
                        readonly = true
                    }
                    + " (Editable in opencola-server.yaml)"
                }
                p {
                    +"Password:"
                    passwordInput(name = "password")
                }
                p {
                    submitInput { value = "Start" }
                }
            }
            p {
                a {
                    href = "/changePassword"
                    +"Change Password"
                }
            }
        }
    }
}

suspend fun changePasswordForm(call: ApplicationCall, isNewUser: Boolean, message: String? = null) {
    call.respondHtml {
        body {
            form(action = "/changePassword", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                if(isNewUser) {
                    p {
                        +"Welcome to OpenCola! Please set a password."
                    }
                }
                if(message != null) {
                    p {
                        +message
                    }
                }
                if(!isNewUser) {
                    p {
                        +"Old Password:"
                        passwordInput(name = "oldPassword")
                    }
                }
                p {
                    +"Password:"
                    passwordInput(name = "password")
                }
                p {
                    +"Confirm:"
                    passwordInput(name = "passwordConfirm")
                }
                p {
                    submitInput() { value = "Change Password" }
                }
            }
        }
    }
}

suspend fun startingPage(call: ApplicationCall) {
    call.respondHtml {
        body {
            +"OpenCola is starting..."
            script {
                unsafe {
                    raw("""
                        setTimeout("window.location = '/';",5000);
                        """)
                }
            }
        }
    }
}