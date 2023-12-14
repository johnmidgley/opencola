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