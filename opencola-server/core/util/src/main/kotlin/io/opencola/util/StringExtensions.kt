package io.opencola.util

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

fun String.hexStringToByteArray(): ByteArray {
    // TODO: This may be more direct
    // return BigInteger(this, 16).toByteArray()
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

inline fun <reified T> String.decodeJson() : T {
    return Json.decodeFromString(this)
}
