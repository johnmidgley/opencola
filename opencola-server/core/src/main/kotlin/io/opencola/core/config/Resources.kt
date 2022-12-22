package io.opencola.core.config

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
    val path = getResourceFilePath(resourcePath, createTempDirectory("oc-resources"), overwriteExistingFiles).toFile()
    path.copyRecursively(destinationPath.toFile())
}