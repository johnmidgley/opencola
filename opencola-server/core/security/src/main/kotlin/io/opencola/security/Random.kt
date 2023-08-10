package io.opencola.security

import java.security.SecureRandom

fun SecureRandom.nextBytes(numBytes: Int) : ByteArray {
    return ByteArray(numBytes).also { this.nextBytes(it) }
}