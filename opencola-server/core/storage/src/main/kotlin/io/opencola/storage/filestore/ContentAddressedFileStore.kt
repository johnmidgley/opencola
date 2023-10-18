package io.opencola.storage.filestore

import io.opencola.model.Id
import java.io.InputStream


// TODO: Only need stream based implementations - ByteArray ones can be defined in terms of those. See IdBasedFileStore
interface ContentAddressedFileStore {
    fun exists(dataId: Id) : Boolean

    fun read(dataId: Id) : ByteArray?
    fun getInputStream(dataId: Id): InputStream?

    fun write(bytes: ByteArray) : Id
    fun write(inputStream: InputStream) : Id

    fun delete(dataId: Id)
    // TODO: getMerkleTree and getPart(s)
}