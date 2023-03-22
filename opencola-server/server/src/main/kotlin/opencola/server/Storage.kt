package opencola.server

import io.opencola.application.copyResources
import io.opencola.storage.getStoragePath
import java.nio.file.Path
import kotlin.io.path.exists

fun initStorage(argPath: String) : Path {
    val storagePath = getStoragePath(argPath)

    if(!storagePath.exists()) {
        copyResources("storage", storagePath, true)
    }

    return storagePath
}