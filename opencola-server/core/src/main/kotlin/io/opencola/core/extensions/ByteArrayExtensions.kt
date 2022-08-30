package opencola.core.extensions

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
