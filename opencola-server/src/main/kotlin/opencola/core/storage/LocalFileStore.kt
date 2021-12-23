package opencola.core.storage

import opencola.core.model.Id
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.*


class LocalFileStore(private val root: Path) : FileStore {
    private val directoryPrefixLength = 2
    private fun getPath(dataId: Id): Path {
        // Organize datafiles like git
        val dataIdString = dataId.toString()
        val directory = Path(root.pathString, dataIdString.substring(0,directoryPrefixLength))

        if(!directory.exists()){
            directory.createDirectory()
        }

        return Path(directory.pathString, dataIdString.substring(2))
    }

    override fun exists(dataId: Id) : Boolean {
        return getPath(dataId).exists()
    }

    override fun getInputStream(dataId: Id): InputStream {
        // TODO: Check options
        return getPath(dataId).inputStream()
    }

    override fun read(dataId: Id): ByteArray {
        getInputStream(dataId).use {
            return it.readAllBytes()
        }
    }

    override fun write(bytes: ByteArray) : Id {
        val dataId = Id(bytes)

        if(exists(dataId)){
            return dataId
        }

        // TODO: Check all uses of streams to make sure properly disposed (i.e. within use
        getPath(dataId).outputStream().use {
            it.write(bytes)
        }

        return Id(bytes)
    }

    override fun write(inputStream: InputStream): Id {
        // TODO: To handle big files, write to tmp file, compute hash and move to proper location
        val bytes = inputStream.readAllBytes()
        return write(bytes)
    }
}