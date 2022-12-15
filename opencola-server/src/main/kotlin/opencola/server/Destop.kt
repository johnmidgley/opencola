package opencola.server

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.*
import io.opencola.core.config.Config
import io.opencola.core.system.openUri
import java.net.URI
import java.nio.file.Path

fun startDesktopApp(storagePath: Path, config: Config) = application {
    val icon = painterResource("icons/opencola.png")

    Window(
        title = "OpenCola",
        onCloseRequest = ::exitApplication,
        visible = false,
        ) { }

    if(isTraySupported) {
        Tray(
            icon = icon,
            menu = {
                Item(
                    text = "Feed",
                    onClick = { openUri(URI("http://localhost:${config.server.port}")) }
                )
                Separator()
                Item(
                    text = "Exit",
                    onClick = ::exitApplication
                )
            }
        )
    }

    startServer(storagePath, config)
}