package opencola.server

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import io.opencola.core.config.Config
import io.opencola.core.system.openUri
import java.net.URI
import java.nio.file.Path

object TrayIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        drawOval(Color(0xFFFFA500))
    }
}

fun startDesktopApp(storagePath: Path, config: Config) = application {
    Tray(
        icon = TrayIcon,
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