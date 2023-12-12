package io.opencola.storage

import io.opencola.model.Id
import io.opencola.storage.filestore.ContentAddressedFileStore
import java.io.InputStream

// TODO: Move to MemoryContentAddressedFileStore and out of test
class MockContentAddressedFileStore : ContentAddressedFileStore {
    private val files = mutableMapOf<Id, ByteArray>()

    override fun exists(dataId: Id): Boolean {
        return files.containsKey(dataId)
    }

    override fun read(dataId: Id): ByteArray? {
        return files[dataId]
    }

    override fun getDataIds(): Sequence<Id> {
        return files.keys.asSequence()
    }

    override fun getInputStream(dataId: Id): InputStream? {
        return files[dataId]?.inputStream()
    }

    override fun write(bytes: ByteArray): Id {
        return Id.ofData(bytes).also { files[it] = bytes }
    }

    override fun write(inputStream: InputStream): Id {
        return write(inputStream.readBytes())
    }

    override fun delete(dataId: Id) {
        files.remove(dataId)
    }
}