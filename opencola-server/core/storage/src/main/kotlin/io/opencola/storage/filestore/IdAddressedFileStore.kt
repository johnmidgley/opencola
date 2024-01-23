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

package io.opencola.storage.filestore

import io.opencola.model.Id
import java.io.InputStream

// TODO: Can probably used to back ContentBasedFileStore
interface IdAddressedFileStore {
    fun exists(id: Id) : Boolean

    fun getInputStream(id: Id): InputStream?
    fun getOutputStream(id: Id): java.io.OutputStream

    fun read(id: Id) : ByteArray? {
        getInputStream(id).use {
            return it?.readAllBytes()
        }
    }

    fun write(id: Id, bytes: ByteArray) {
        // TODO Compression?
        getOutputStream(id).use {
            it.write(bytes)
        }
    }

    fun delete(id: Id)
}