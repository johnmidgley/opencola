package opencola.server

import io.opencola.application.copyResources
import io.opencola.storage.getStoragePath
import java.nio.file.Path

fun initStorage(argPath: String) : Path {
    val storagePath = getStoragePath(argPath)
     copyResources("storage", storagePath, false)
    return storagePath
}