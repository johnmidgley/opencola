package io.opencola.storage

import io.opencola.application.TestApplication
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore

fun newContentAddressedFileStore(name: String): ContentAddressedFileStore {
    val directory = TestApplication.getTmpDirectory("-$name-filestore")
    return FileSystemContentAddressedFileStore(directory)
}