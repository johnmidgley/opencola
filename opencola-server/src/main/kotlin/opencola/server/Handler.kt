package opencola.server

import io.ktor.application.*
import opencola.core.config.Application

abstract class Handler(val call: ApplicationCall) {
    val application = Application.instance
    val injector = application.injector

    abstract suspend fun respond()
}