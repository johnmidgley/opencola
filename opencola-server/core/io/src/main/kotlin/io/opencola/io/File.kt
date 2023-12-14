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

