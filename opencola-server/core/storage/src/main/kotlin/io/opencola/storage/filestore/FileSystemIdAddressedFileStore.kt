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
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.*

class FileSystemIdAddressedFileStore(val root: Path) : IdAddressedFileStore {
    private val directoryPrefixLength = 2

    private fun getPath(id: Id, createDirectory: Boolean = false): Path {
        val dataIdString = id.toString()

        // Organize datafiles like git
        val directory = Path(root.pathString, dataIdString.substring(0,directoryPrefixLength))

        if(!directory.exists() && createDirectory){
            directory.createDirectory()
        }

        return Path(directory.pathString, dataIdString.substring(2))
    }

    override fun exists(id: Id): Boolean {
        return getPath(id).exists()
    }

    override fun getInputStream(id: Id): InputStream? {
        return getPath(id).let { if(it.exists()) it.inputStream() else null }
    }

    override fun getOutputStream(id: Id): OutputStream {
        return getPath(id, true).outputStream()
    }

    override fun delete(id: Id) {
        getPath(id).deleteIfExists()
    }
}