package opencola.server.view

import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.body
import kotlinx.html.script
import kotlinx.html.unsafe

suspend fun loggedIn(call: ApplicationCall) {
    call.respondHtml {
        body {
            +"You are now logged in. This window will close momentarily."
            script {
                unsafe {
                    raw("""
                        window.close()
                        """)
                }
            }
        }
    }
}