/*
 * Copyright 2024-2026 OpenCola
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

package io.opencola.storage

import io.opencola.system.OS
import io.opencola.system.getOS
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

fun getDefaultStoragePathForOS(): Path {
    val userHome = Path(System.getProperty("user.home"))

    return when (getOS()) {
        OS.Mac -> userHome.resolve("Library/Application Support/OpenCola/storage")
        OS.Windows -> userHome.resolve("AppData/Local/OpenCola/storage")
        else -> userHome.resolve(".opencola/storage")
    }
}

fun getStoragePath(argPath: String) : Path {
    val homeStoragePath = Path(System.getProperty("user.home")).resolve(".opencola/storage")

    return if (argPath.isNotBlank()) {
        // Storage path has been explicitly set, so use it
        Path(argPath)
    } else if (homeStoragePath.exists()) {
        // The default original storage location is present, so use it
        homeStoragePath
    } else {
        // Fall back to OS specific default paths
        getDefaultStoragePathForOS()
    }
}