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

package io.opencola.relay.server

import java.net.Inet4Address
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class RelayContext(val workingDirectory: Path) {
    val address: URI
    val config: RelayConfig
    val absoluteStoragePath : Path

    init {
        require(workingDirectory.exists() && workingDirectory.isDirectory()) { "Working directory must exist and be a directory: $workingDirectory" }
        config = loadConfig(workingDirectory.resolve("opencola-relay.yaml")).relay
        address = URI("ocr://${Inet4Address.getLocalHost().hostAddress}:${config.server.port}")
        absoluteStoragePath = workingDirectory.resolve(config.storagePath)
    }

    override fun toString(): String {
        return "RelayContext(workingDirectory=$workingDirectory, address=$address, config=$config, absoluteStoragePath=$absoluteStoragePath)"
    }
}