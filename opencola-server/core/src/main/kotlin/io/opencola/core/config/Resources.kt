package io.opencola.core.config

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

fun extractResourceDirectory(resourceUrl: URL, destinationPath: Path): Path {
    val parts = resourceUrl.path.split("!")
    val jarPath = URL(parts[0]).path
    val resourcePath = parts[1].trim('/') + "/"
    val jarFile = JarFile(jarPath)

    destinationPath.createDirectories()

    jarFile.entries().asIterator().forEach {
        if(it.name.startsWith(resourcePath)) {
            if(it.isDirectory) {
                destinationPath.resolve(it.name).createDirectories()
            } else {
                destinationPath.resolve(it.name).writeBytes(getResourceAsStream(it.name)!!.readAllBytes())
            }
        }
    }

    return destinationPath.resolve(resourcePath)
}

fun getResourcePath(resourcePath: String) : Path {
     // val root = URL("jar:file:/Applications/OpenCola.app/Contents/app/opencola-server-1.0-SNAPSHOT.jar!/$resourcePath")
     val root = object {}.javaClass.classLoader.getResource(resourcePath)
        ?: throw IllegalStateException("Unable to locate root resource: $resourcePath")

    return when (root.protocol) {
        "file" ->
            // Resources are available on the filesystem, so just return local path
            Path(root.path)
        "jar" -> {
            extractResourceDirectory(root, Path("resource-cache"))
        }
        else ->
            throw RuntimeException("Don't know how to handle resource protocol: ${root.protocol}")
    }
}