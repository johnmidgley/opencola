package io.opencola.util

import java.net.URI

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
