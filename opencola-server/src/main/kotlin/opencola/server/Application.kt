package opencola.server

import com.sksamuel.hoplite.ConfigLoader
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.core.config.Application
import opencola.core.config.Config
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import org.kodein.di.DI
import kotlin.io.path.Path

fun main() {
    val path = Path(System.getProperty("user.dir"))
    val config: Config = ConfigLoader().loadConfigOrThrow(path.resolve("opencola-server.yaml"))
    val injector = DI {

    }
    Application.instance = Application(path, config, injector)

    embeddedServer(Netty, port = 5795, host = "0.0.0.0") {
        configureHTTP()
        configureContentNegotiation()
        configureRouting()
    }.start(wait = true)
}
