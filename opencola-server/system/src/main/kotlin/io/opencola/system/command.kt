package io.opencola.system

import io.opencola.io.MultiStreamReader
import java.io.File
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.Path

fun startProcess(args: List<String>, workingDir: Path = Path(".")): Process? {
    return ProcessBuilder(args)
        .directory(File(workingDir.toString()))
        .start()
}

fun runCommand(args: List<String>, workingDir: Path = Path("."), timeoutInSeconds: Long = 3600, printOutput: Boolean = false) : List<String> {
    val process = startProcess(args, workingDir) ?: throw RuntimeException("Unable to start run command: ${args.joinToString(" ")}")
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

        if(process.exitValue() != 0) {
            throw RuntimeException("Command failed (${process.exitValue()}) ${args.joinToString(" ")}: ${outputLines.joinToString("\n")}")
        }
    }

    process.destroy()
    return outputLines
}