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

package io.opencola.io

import java.io.FileInputStream
import java.nio.file.*
import kotlin.io.path.isDirectory

fun copyDirectory(source: Path, target: Path) {
    source.toFile().walkTopDown().forEach {
        val targetFile = target.resolve(source.relativize(it.toPath()))
        if (it.isDirectory) {
            targetFile.toFile().mkdirs()
        } else {
            it.copyTo(targetFile.toFile(), true)
        }
    }
}

// Read a file one block at a time.
// WARNING: For efficiency, the SAME buffer is re-used in the sequence, as there is clear
// intention to not need all the blocks at the same time (otherwise reading the whole file is
// much more convenient).
fun readFileBlocks(path: String, blockSize: Int = 1024): Sequence<ByteArray> {
    return sequence {
        FileInputStream(path).use { fis ->
            val buffer = ByteArray(blockSize)
            var bytesRead: Int

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                yield(if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead))
            }
        }
    }
}

fun isDirectoryEmpty(path: Path): Boolean {
    if (!path.isDirectory())
        return false

    Files.newDirectoryStream(path).use { return !it.iterator().hasNext() }
}

