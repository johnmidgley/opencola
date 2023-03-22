package io.opencola.io

import java.nio.file.Path

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