package opencola.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.core.extensions.nullOrElse
import opencola.core.security.publicKeyFromBytes
import java.net.URI
import java.nio.ByteBuffer
import java.security.PublicKey

// TODO: Can this be generified?
// TODO: Should this be ValueCodec?
interface ByteArrayCodec<T> {
    fun encode(value: T): ByteArray
    fun decode(value: ByteArray): T
}

object BooleanByteArrayCodec : ByteArrayCodec<Boolean> {
    // TODO: Can these be bytes instead?
    private const val FALSE = 0
    private const val TRUE = 1

    override fun encode(value: Boolean): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(if (value) TRUE else FALSE).array()
    }

    override fun decode(value: ByteArray): Boolean {
        return ByteBuffer.wrap(value).int == TRUE
    }
}

object FloatByteArrayCodec : ByteArrayCodec<Float> {
    override fun encode(value: Float): ByteArray {
        return ByteBuffer.allocate(Float.SIZE_BYTES).putFloat(value).array()
    }

    override fun decode(value: ByteArray): Float {
        return ByteBuffer.wrap(value).float
    }
}

object IntByteArrayCodec : ByteArrayCodec<Int> {
    override fun encode(value: Int): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
    }

    override fun decode(value: ByteArray): Int {
        return ByteBuffer.wrap(value).int
    }
}

object LongByteArrayCodec : ByteArrayCodec<Long> {
    override fun encode(value: Long): ByteArray {
        return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()
    }

    override fun decode(value: ByteArray): Long {
        return ByteBuffer.wrap(value).long
    }
}

object StringByteArrayCodec : ByteArrayCodec<String> {
    override fun encode(value: String): ByteArray {
        return value.toByteArray()
    }

    override fun decode(value: ByteArray): String {
        return String(value)
    }
}

object UriByteArrayCodec : ByteArrayCodec<URI> {
    override fun encode(value: URI): ByteArray {
        return value.toString().toByteArray()
    }

    override fun decode(value: ByteArray): URI {
        return URI(String(value))
    }
}

object SetOfStringByteArrayCodec : ByteArrayCodec<Set<String>> {
    override fun encode(value: Set<String>): ByteArray {
        // TODO: Get rid of json encoder.
        return Json.encodeToString(value as Set<String>).toByteArray()
    }

    override fun decode(value: ByteArray): Set<String> {
        return Json.decodeFromString(String(value))
    }
}

object PublicKeyByteArrayCodec : ByteArrayCodec<PublicKey> {
    override fun encode(value: PublicKey): ByteArray {
        return value.encoded
    }

    override fun decode(value: ByteArray): PublicKey {
        return publicKeyFromBytes(value)
    }
}