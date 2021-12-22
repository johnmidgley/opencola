package opencola.core.extensions

// TODO: Figure out how to get rid of this and byte extensions these are just encoders
fun String.hexStringToByteArray(): ByteArray {
    return this.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}