package opencola.core.storage

import opencola.core.model.Id
import java.io.InputStream


interface FileStore {
    fun exists(dataId: Id) : Boolean

    fun read(dataId: Id) : ByteArray
    fun getInputStream(dataId: Id): InputStream

    fun write(bytes: ByteArray) : Id
    fun write(inputStream: InputStream) : Id

    // TODO: getMerkleTree and getPart(s)
}