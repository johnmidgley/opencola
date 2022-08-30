package io.opencola.core.network.providers.zerotier

import opencola.core.extensions.toHexString
import io.opencola.core.serialization.LongByteArrayCodec
import java.math.BigInteger

class ZeroTierId(private val id: BigInteger) {
    constructor(id: String) : this(BigInteger(id, 16))

    // Zero tier ids aren't "proper" Longs in Java (e.g. d3ecf5726d5f15f6 converted to long and back to hex is
    // -2c130a8d92a0ea0a). To properly construct off a long, write out "unsigned" hex and read it into a BigInteger
    constructor(id: Long) : this(BigInteger(LongByteArrayCodec.encode(id).toHexString(), 16))

    override fun toString(): String {
        return id.toString(16)
    }

    fun toLong(): Long {
        return id.toLong()
    }
}