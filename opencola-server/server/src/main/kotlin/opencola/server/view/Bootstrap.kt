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
                href = "css/main.css"
            }
            script { src = "js/main.js" }
        }
        body {
            form(action = "/start", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
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
                    submitInput { value = "Start" }
                }
            }
            // script { unsafe { raw("onSubmitHashFormFields(document.querySelector('form'), ['password'])") } }
            p {
                a {
                    href = "/changePassword"
                    +"Change Password"
                }
                br { }
                a {
                    href = "/installCert.html"
                    +"Reinstall SSL Certificate"
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
                href = "css/main.css"
            }
            script { src = "js/main.js" }
        }
        body {
            form(action = "/newUser", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                p {
                    h2 { +"Welcome to OpenCola!" }
                }
                if (message != null) {
                    p {
                        +message
                    }
                } else {
                    p {
                        +"Please set a password to protect your private keys (be sure to not lose this - it can't be recovered!):"
                    }
                }

                table {
                    tr {
                        td {
                            +"Password:"
                        }
                        td {
                            passwordInput(name = "password")
                        }
                    }
                    tr {
                        td {
                            +"Confirm:"
                        }
                        td {
                            passwordInput(name = "passwordConfirm")
                        }
                    }
                    if (!runningInDocker()) {
                        tr {
                            td {
                                +"Auto-Start:"
                            }
                            td {
                                checkBoxInput {
                                    name = "autostart"
                                    value = "true"
                                    checked = true
                                }
                            }
                        }
                    }
                }
                p {
                    submitInput { value = "Start" }
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
                href = "css/main.css"
            }
            script { src = "js/main.js" }
        }
        body {
            form(
                action = "/changePassword",
                encType = FormEncType.applicationXWwwFormUrlEncoded,
                method = FormMethod.post
            ) {
                if (message != null) {
                    p {
                        +message
                    }
                }
                table {
                    tr {
                        td {
                            +"Current Password:"
                        }
                        td {
                            passwordInput(name = "password")
                        }
                    }
                    tr {
                        td {
                            +"New Password:"
                        }
                        td {
                            passwordInput(name = "newPassword")
                        }
                    }
                    tr {
                        td {
                            +"Confirm:"
                        }
                        td {
                            passwordInput(name = "newPasswordConfirm")
                        }
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

suspend fun startingPage(call: ApplicationCall, authToken: String, migratingData: Boolean) {
    call.sessions.set(UserSession(authToken))
    call.respondHtml {
        head {
            link {
                rel = "stylesheet"
                href = "css/main.css"
            }
            script { src = "js/main.js" }
        }
        body {
            +"OpenCola is carbonating..."
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