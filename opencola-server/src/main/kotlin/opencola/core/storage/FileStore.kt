package opencola.core.storage

import opencola.core.model.Id
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.*

class FileStore(private val root: Path) {
    private fun getPath(dataId: Id): Path {
        // TODO: create directory structure like git, where first 2 chars are subdirectory
        return Path(root.pathString,dataId.toString())
    }

    fun fileExists(dataId: Id) : Boolean {
        return getPath(dataId).exists()
    }

    fun getOutputStream(dataId: Id): OutputStream {
        // TODO: Never let a file be overwritten - check for existence first
        // TODO: Set create option
        return getPath(dataId).outputStream()
    }

    fun getInputStream(dataId: Id): InputStream {
        // TODO: Check options
        return getPath(dataId).inputStream()
    }
}