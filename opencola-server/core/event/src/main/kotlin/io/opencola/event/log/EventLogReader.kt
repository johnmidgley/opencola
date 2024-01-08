package io.opencola.event.log

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory

fun readFile(path: Path): Sequence<EventLogEntry> {
    require(path.toFile().isFile) { "Path must be a file" }
    return sequence {
        path.inputStream().use {
            it.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    yield(Json.decodeFromString(line))
                }
            }
        }
    }
}

fun readDirectory(path: Path): Sequence<EventLogEntry> {
    require(path.toFile().isDirectory) { "Path must be a directory" }

    return sequence {
        path.toFile().walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "log") {
                readFile(file.toPath()).forEach { entry ->
                    yield(entry)
                }
            }
        }
    }
}

fun readEventLogEntries(path: Path) : Sequence<EventLogEntry> {
    return if(path.isDirectory())
        readDirectory(path)
    else if(path.toFile().isFile)
        readFile(path)
    else {
        throw IllegalArgumentException("Path must be a file or directory")
    }
}

fun summarize(path: Path): HashMap<String, Int> {
    return readEventLogEntries(path).fold(HashMap<String, Int>()) { counts, entry ->
        counts[entry.name] = (counts[entry.name] ?: 0) + 1
        counts
    }
}