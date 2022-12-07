package opencola.server

import io.opencola.core.config.copyResources
import io.opencola.core.system.OS
import io.opencola.core.system.getOS
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

fun initStorage(argPath: String) : Path {
    val homeStoragePath = Path(System.getProperty("user.home")).resolve(".opencola/storage")

    val storagePath =
        if(argPath.isNotBlank()) {
            // Storage path has been explicitly set, so use it
            Path(argPath)
        } else if(homeStoragePath.exists()) {
            // The default original storage location is present, so use it
            homeStoragePath
        } else {
            // Fall back to OS specific default paths
            getDefaultStoragePathForOS()
        }

    if(!storagePath.exists()) {
        copyResources("storage", storagePath)
    }

    return storagePath
}