/*
 * Copyright 2024-2026 OpenCola
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


// TODO: Only need stream based implementations - ByteArray ones can be defined in terms of those. See IdBasedFileStore
// TODO: Make this look like IdAddressedFileStore, possibly merging interfaces
interface ContentAddressedFileStore {
    fun exists(dataId: Id) : Boolean

    fun read(dataId: Id) : ByteArray?
    fun getInputStream(dataId: Id): InputStream?

    fun write(bytes: ByteArray) : Id
    fun write(inputStream: InputStream) : Id

    fun delete(dataId: Id)
    // TODO: getMerkleTree and getPart(s)

    fun enumerateIds(): Sequence<Id>
}