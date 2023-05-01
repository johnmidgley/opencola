package io.opencola.util

import java.io.ByteArrayOutputStream

fun ByteArray.toHexString() : String {
    // TODO: Use a string builder and make lazy / memoized
    return this.fold("") { str, it -> str + "%02x".format(it) }
}

fun ByteArray.append(other: ByteArray) : ByteArray {
    return ByteArrayOutputStream().use {
        it.write(this)
        it.write(other)
        it.toByteArray()
    }
}

fun ByteArray.compareTo(other: ByteArray): Int {
    val minLength = minOf(this.size, other.size)

    for (i in 0 until minLength) {
        val result = this[i].compareTo(other[i])
        if (result != 0) {
            return result
        }
    }

    return this.size.compareTo(other.size)
}