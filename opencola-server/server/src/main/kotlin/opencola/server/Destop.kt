/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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