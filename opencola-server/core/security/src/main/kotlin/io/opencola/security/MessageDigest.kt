package io.opencola.security

import java.security.MessageDigest

fun sha256(input: ByteArray) : ByteArray{
    // Can use update() method to add in length
    return MessageDigest
        .getInstance("SHA-256")
        .digest(input)
}

fun sha256(input: String): ByteArray {
    return sha256(input.toByteArray())
}