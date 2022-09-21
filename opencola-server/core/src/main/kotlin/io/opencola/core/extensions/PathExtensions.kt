package io.opencola.core.extensions

import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory

fun Path.recursiveDelete() {
    if (this.isDirectory()) {
        this.toFile().listFiles()?.forEach {
            if (it.isDirectory) {
                it.toPath().recursiveDelete()
            }

            it.delete()
        }
    } else
        this.deleteIfExists()
}