package opencola.server

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import io.opencola.core.config.Config
import io.opencola.core.system.openUri
import java.net.URI
import java.nio.file.Path

fun startDesktopApp(storagePath: Path, config: Config) = application {
    val icon = painterResource("pulltab-icon.png")
    Tray(
        icon = icon,
        menu = {
            Item(
                text = "Feed",
                onClick = { openUri(URI("http://localhost:5795")) }
            )
            Separator()
            Item(
                text = "Exit",
                onClick = ::exitApplication
            )
        }
    )

    startServer(storagePath, config) { exitApplication() }
}