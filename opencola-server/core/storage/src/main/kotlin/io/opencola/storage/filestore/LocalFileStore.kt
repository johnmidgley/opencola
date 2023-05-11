package io.opencola.storage.filestore

import io.opencola.model.Id
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.*


class LocalFileStore(private val root: Path) : FileStore {
    private val directoryPrefixLength = 2

    init {
        if(!root.exists()){
            root.createDirectory()
        }
    }

    private fun getPath(dataIdString: String, createDirectory: Boolean = false): Path {
        // Organize datafiles like git
        val directory = Path(root.pathString, dataIdString.substring(0,directoryPrefixLength))

        if(!directory.exists() && createDirectory){
            directory.createDirectory()
        }

        return Path(directory.pathString, dataIdString.substring(2))
    }

    private fun getPath(dataId: Id, createDirectory: Boolean = false) : Path {
        val dataIdString = dataId.toString()
        val path = getPath(dataIdString, createDirectory)

        if(!createDirectory && !path.exists()){
            // This is a read, so check by legacy hex id, and move if exists
            val legacyPath = getPath(dataId.legacyEncode())
            if(legacyPath.exists())
                legacyPath.moveTo(getPath(dataIdString, true))
        }

        return path
    }

    override fun exists(dataId: Id) : Boolean {
        return getPath(dataId).exists()
    }

    override fun getInputStream(dataId: Id): InputStream? {
        // TODO: Check options
        return getPath(dataId).let { if(it.exists()) it.inputStream() else null }
    }

    override fun read(dataId: Id): ByteArray? {
        getInputStream(dataId).use {
            return it?.readAllBytes()
        }
    }

    override fun write(bytes: ByteArray) : Id {
        val dataId = Id.ofData(bytes)

        if(exists(dataId)){
            return dataId
        }

        // TODO: Check all uses of streams to make sure properly disposed (i.e. within use
        getPath(dataId, true).outputStream().use {
            it.write(bytes)
        }

        return dataId
    }

    override fun write(inputStream: InputStream): Id {
        // TODO: To handle big files, write to tmp file, compute hash and move to proper location
        val bytes = inputStream.readAllBytes()
        return write(bytes)
    }
}