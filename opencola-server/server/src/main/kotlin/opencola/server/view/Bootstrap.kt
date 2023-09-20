package opencola.server.view

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.sessions.*
import io.opencola.system.runningInDocker
import kotlinx.html.*
import opencola.server.plugins.UserSession

suspend fun startupForm(call: ApplicationCall, message: String? = null) {
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
                form(action = "/start", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                    p {
                        +"Enter your password to start OpenCola"
                    }
                    p {
                        // +"Password:"
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
                        submitInput { value = "Start" }
                    }
                }
                p {
                    a {
                        href = "/changePassword"
                        +"Change Password"
                    }
                    br { }
                    a {
                        href = "/init/installCert.html"
                        +"Reinstall SSL Certificate"
                    }
                }
            }
        }
    }
}

suspend fun newUserForm(call: ApplicationCall, message: String? = null) {
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
                form(
                    action = "/newUser",
                    encType = FormEncType.applicationXWwwFormUrlEncoded,
                    method = FormMethod.post
                ) {
                    span {
                        style = "font-weight: bold;"
                        +"Create a Password"
                    }
                    p {
                        +"Please set a password to protect your private keys (be sure to not lose this - it can't be recovered!)"
                    }
                    p {
                        passwordInput(name = "password") {
                            placeholder = "Password"
                        }
                        passwordInput(name = "passwordConfirm") {
                            placeholder = "Confirm Password"
                        }
                        if (!runningInDocker()) {
                            span {
                                +"Auto-Start:"
                                checkBoxInput {
                                    name = "autostart"
                                    value = "true"
                                    checked = true
                                }
                            }
                        }
                    }
                    if (message != null) {
                        p {
                            classes = setOf("error")
                            +message
                        }
                    }
                    p {
                        submitInput { value = "Start" }
                    }
                }
                // script { unsafe { raw("onSubmitHashFormFields(document.querySelector('form'), ['password', 'passwordConfirm'])") } }
            }
        }
    }
}

suspend fun changePasswordForm(call: ApplicationCall, message: String? = null) {
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
                form(
                    action = "/changePassword",
                    encType = FormEncType.applicationXWwwFormUrlEncoded,
                    method = FormMethod.post
                ) {
                    p {
                        +"Change Password"
                    }
                    p {
                        passwordInput(name = "password") {
                            placeholder = "Password"
                        }
                        passwordInput(name = "newPassword") {
                            placeholder = "New Password"
                        }
                        passwordInput(name = "newPasswordConfirm") {
                            placeholder = "Confirm New Password"
                        }
                    }
                    if (message != null) {
                        p {
                            classes = setOf("error")
                            +message
                        }
                    }
                    p {
                        submitInput() { value = "Change Password" }
                    }
                    // script { unsafe { raw("onSubmitHashFormFields(document.querySelector('form'), ['password', 'newPassword', 'newPasswordConfirm'])") } }
                }
            }
        }
    }
}

suspend fun startingPage(call: ApplicationCall, authToken: String, migratingData: Boolean) {
    call.sessions.set(UserSession(authToken))
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
                p {
                    +"OpenCola is carbonating..."
                }
                if (migratingData)
                    p {
                        style = "color: red; font-weight: bold;"
                        text("Your data is being migrated to a new storage format. This may take a few minutes...")
                    }
                script {
                    unsafe { raw("waitForServerStart();") }
                }
            }
        }
    }
}