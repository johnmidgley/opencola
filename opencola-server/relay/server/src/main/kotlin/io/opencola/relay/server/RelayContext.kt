package io.opencola.relay.server

import io.opencola.relay.common.defaultOCRPort
import java.net.Inet4Address
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class RelayContext(val workingDirectory: Path) {
    val address = URI("ocr://${Inet4Address.getLocalHost().hostAddress}:$defaultOCRPort")
    val config : Config
    val absoluteStoragePath : Path

    init {
        require(workingDirectory.exists() && workingDirectory.isDirectory()) { "Working directory must exist and be a directory: $workingDirectory" }
        config = loadConfig(workingDirectory.resolve("opencola-relay.yaml"))
        absoluteStoragePath = workingDirectory.resolve(config.storagePath)
    }

    override fun toString(): String {
        return "RelayContext(workingDirectory=$workingDirectory, address=$address, config=$config, absoluteStoragePath=$absoluteStoragePath)"
    }
}