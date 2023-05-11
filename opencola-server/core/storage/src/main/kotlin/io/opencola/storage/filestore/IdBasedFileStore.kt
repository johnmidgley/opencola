package io.opencola.storage.filestore

import io.opencola.model.Id
import java.io.InputStream

// TODO: Can probably used to back ContentBasedFileStore
interface IdBasedFileStore {
    fun exists(id: Id) : Boolean

    fun getInputStream(id: Id): InputStream?
    fun getOutputStream(id: Id): java.io.OutputStream

    fun read(id: Id) : ByteArray? {
        TODO("Compression?") // With Proto type that indicates compression.
        getInputStream(id).use {
            return it?.readAllBytes()
        }
    }

    fun write(id: Id, bytes: ByteArray) {
        TODO("Compression?")
        getOutputStream(id).use {
            it.write(bytes)
        }
    }
}