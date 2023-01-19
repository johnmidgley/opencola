package opencola.server

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.*
import io.opencola.application.Config
import io.opencola.system.openUri
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
                    onClick = { openUri(URI("https://localhost:${config.server.ssl?.port ?: 5796}")) }
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