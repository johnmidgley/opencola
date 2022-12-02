package io.opencola.core.extensions

import io.opencola.core.io.MultiStreamReader
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.*
import java.time.Instant

// TODO: Figure out how to get rid of this and byte extensions these are just encoders
fun String.hexStringToByteArray(): ByteArray {
    return this.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun String.tryParseUri() : URI? {
    return try{
        URI(this)
    }catch (e: Exception){
        null
    }
}

fun String?.blankToNull() : String? {
    return if(this.isNullOrBlank()) null else this
}

fun String.startProcess(workingDir: Path): Process? {
    // TODO: Problem here - splitting on " " in the middle of a filename "e.g. Application\ Support" causes problems.
    //  Should only split on spaces that aren't escaped.
    return ProcessBuilder(*split(" ").toTypedArray())
        .directory(File(workingDir.toString()))
        .start()
}

fun String.runCommand(workingDir: Path = Path("."), timeoutInSeconds: Long = 3600, printOutput: Boolean = false) : List<String> {
    val process = startProcess(workingDir) ?: throw RuntimeException("Unable to start run command: $this")
    val outputLines = MutableList(0) { "" }

    MultiStreamReader(process.inputStream, process.errorStream).use { reader ->
        val startTime = Instant.now().epochSecond
        var stillReading = true
        while ((process.isAlive && Instant.now().epochSecond - startTime < timeoutInSeconds) || stillReading) {
            val line = reader.readLine()

            if(line == null) {
                stillReading = false
                // This is not great, but since we have no way to interact with the
                // external process, we have to poll for input
                Thread.sleep(50)
            } else {
                // stillReading makes sure we consume all available output, even if the process
                // is no longer alive
                stillReading = true
                outputLines.add(line)
                if (printOutput) println(line)
            }
        }
    }

    return outputLines
}
