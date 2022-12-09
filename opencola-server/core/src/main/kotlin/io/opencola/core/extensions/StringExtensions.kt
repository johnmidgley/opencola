package io.opencola.core.extensions

import java.net.URI
import java.nio.file.Path
import io.opencola.core.system.runCommand
import kotlin.io.path.*

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

// TODO: Remove this. Too many problems with string handling. Replace calls with runCommand in io.opencola.core.system
fun String.runCommand(workingDir: Path = Path(".",), timeoutInSeconds: Long = 3600, printOutput: Boolean = false) : List<String> {
    val args = "(?<!\\\\) ".toRegex().split(this).map { it.replace("\\ ", " ") }
    return runCommand(args, workingDir, timeoutInSeconds, printOutput)
}
