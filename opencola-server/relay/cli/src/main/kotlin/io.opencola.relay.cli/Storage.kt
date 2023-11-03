package io.opencola.relay.cli

import io.opencola.storage.getStoragePath
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

fun initStorage(): Path {
    val storagePath = getStoragePath("").resolve("ocr")

    if (!storagePath.exists()) {
        println("Creating storage path: $storagePath")
        storagePath.createDirectories()
    }

    return storagePath
}