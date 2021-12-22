package opencola.core.extensions

fun ByteArray.toHexString() : String {
    // TODO: Use a string builder and make lazy / memoized
    return this.fold("") { str, it -> str + "%02x".format(it) }
}
