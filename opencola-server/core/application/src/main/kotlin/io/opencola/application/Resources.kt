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

package io.opencola.application

import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.RuntimeException
import kotlin.io.path.*

fun getJarPath(resourceUrl: URL): Path {
    return Path(Path(URL(resourceUrl.path).path).parent.toString().trim('!'))
}

fun getResourceUrl(name: String): URL? {
    return object {}.javaClass.classLoader.getResource(name)
}

fun getResourceAsStream(name: String): InputStream? {
    return object {}.javaClass.classLoader.getResourceAsStream(name)
}

fun extractResourceDirectory(resourceUrl: URL, destinationPath: Path, overwriteExistingFiles: Boolean): Path {
    val parts = resourceUrl.path.split("!")
    val jarPath = File(URL(parts[0]).toURI()).toPath().toString()
    val resourcePath = parts[1].trim('/') + "/"
    val jarFile = JarFile(jarPath)

    destinationPath.createDirectories()

    jarFile.entries().asIterator().forEach {
        if(it.name.startsWith(resourcePath)) {
            if(it.isDirectory) {
                destinationPath.resolve(it.name).createDirectories()
            } else {
                val destinationFile = destinationPath.resolve(it.name)
                if(!destinationFile.exists() || overwriteExistingFiles) {
                    destinationFile.writeBytes(jarFile.getInputStream(it).readBytes())
                }
            }
        }
    }

    return destinationPath.resolve(resourcePath)
}

// fileSystemPath: Path to the directory where the resources should be extracted (if necessary). If the resources are
// already available on the filesystem, this path is ignored.
fun getResourceFilePath(resourcePath: String, fileSystemPath: Path, overwriteExistingFiles: Boolean) : Path {
     val root = getResourceUrl(resourcePath)
        ?: throw IllegalStateException("Unable to locate root resource: $resourcePath")

    return when (root.protocol) {
        "file" -> {
            // Resources are available on the filesystem, so just return local path
            File(root.toURI()).toPath()
        }
        "jar" -> {
            extractResourceDirectory(root, fileSystemPath, overwriteExistingFiles)
        }
        else ->
            throw RuntimeException("Don't know how to handle resource protocol: ${root.protocol}")
    }
}

fun copyResources(resourcePath: String,  destinationPath: Path, overwriteExistingFiles: Boolean) {
    val path = getResourceFilePath(resourcePath, destinationPath, overwriteExistingFiles).toFile()

    // Since we don't know whether getResourceFilePath copied any files (in development mode it just returns an
    // existing path), we do an extra copy to make sure the files end up where they are expected.
    path.copyRecursively(destinationPath.toFile(), overwriteExistingFiles) { _, e ->
        when(e) {
            is FileAlreadyExistsException -> OnErrorAction.SKIP
            else -> throw e
        }
    }
}