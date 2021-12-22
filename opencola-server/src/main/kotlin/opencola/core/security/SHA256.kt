package opencola.core.security

import java.security.MessageDigest

fun sha256(input: ByteArray) : ByteArray{
    // Can use update() method to add in length
    return MessageDigest
        .getInstance("SHA-256") // TODO: SHA 3?
        .digest(input)
}

fun sha256(input: String): ByteArray {
    return sha256(input.toByteArray())
}