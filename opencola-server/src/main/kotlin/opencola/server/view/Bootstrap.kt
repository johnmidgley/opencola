package opencola.server.view

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.sessions.*
import kotlinx.html.*
import opencola.server.UserSession

suspend fun startupForm(call: ApplicationCall, username: String, message: String? = null) {
    call.respondHtml {
        head {
            link {
                rel = "stylesheet"
                href = "css/main.css"
            }
        }
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
            p {
                a {
                    href = "/changePassword"
                    +"Change Password"
                }
                br {  }
                a {
                    href = "/installCert.html"
                    +"Reinstall SSL Certificate"
                }
            }
        }
    }
}

suspend fun newUserForm(call: ApplicationCall, username: String, message: String? = null) {
    call.respondHtml {
        head {
            link {
                rel = "stylesheet"
                href = "css/main.css"
            }
        }
        body {
            form(action = "/newUser", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                p {
                    h2 { +"Welcome to OpenCola!" }
                }
                if(message != null) {
                    p {
                        +message
                    }
                } else {
                    p {
                        +"Please set a username and password:"
                    }
                }

                table {
                    tr {
                        td {
                            +"Username:"
                        }
                        td {
                            textInput {
                                name = "username"
                                value = username
                            }
                        }
                    }
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

                p {
                    submitInput { value = "Start" }
                }
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
        }
        body {
            form(action = "/changePassword", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                if(message != null) {
                    p {
                        +message
                    }
                }
                table {
                    tr {
                        td {
                            +"Old Password:"
                        }
                        td {
                            passwordInput(name = "oldPassword")
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
            }
        }
    }
}

suspend fun startingPage(call: ApplicationCall, username: String) {
    call.sessions.set(UserSession(username, true))
    call.respondHtml {
        head {
            link {
                rel = "stylesheet"
                href = "css/main.css"
            }
        }
        body {
            +"OpenCola is carbonating..."
            script {
                unsafe {
                    raw("""
                        function onImageAvailable( src, onSuccess ) {
                            console.log("Trying")
                    
                            let img = new Image();
                            img.onload = function () {
                                onSuccess();
                            };
                    
                            img.onerror = function () {
                                setTimeout(onImageAvailable, 1000, src, onSuccess);
                            };
                    
                            img.src = src;
                        }
                    
                        onImageAvailable("img/pull-tab.png", function () {
                            window.location = '/';
                        });
                        """)
                }
            }
        }
    }
}