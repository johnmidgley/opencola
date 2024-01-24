/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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