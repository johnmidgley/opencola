package io.opencola.storage.filestore

import io.opencola.model.Id
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.*

class LocalIdBasedFileStore(val root: Path) : IdBasedFileStore {
    private val directoryPrefixLength = 2

    private fun getPath(id: Id, createDirectory: Boolean = false): Path {
        val dataIdString = id.toString()

        // Organize datafiles like git
        val directory = Path(root.pathString, dataIdString.substring(0,directoryPrefixLength))

        if(!directory.exists() && createDirectory){
            directory.createDirectory()
        }

        return Path(directory.pathString, dataIdString.substring(2))
    }

    override fun exists(id: Id): Boolean {
        return getPath(id).exists()
    }

    override fun getInputStream(id: Id): InputStream? {
        return getPath(id).let { if(it.exists()) it.inputStream() else null }
    }

    override fun getOutputStream(id: Id): OutputStream {
        return getPath(id, true).outputStream()
    }

    override fun delete(id: Id) {
        getPath(id).deleteIfExists()
    }
}