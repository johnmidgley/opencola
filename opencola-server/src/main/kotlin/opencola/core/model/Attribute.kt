package opencola.core.model

import java.io.InputStream
import java.io.OutputStream
import java.net.URI

data class Attribute(val name: String, val uri: URI, val codec: ByteArrayCodec, val isIndexable: Boolean){
    constructor(uri: URI, codec: ByteArrayCodec, isIndexable: Boolean) : this(uri.path.split("/").last(), uri, codec, isIndexable)

    companion object Factory : ByteArrayStreamCodec<Attribute?>{
        override fun encode(stream: OutputStream, value: Attribute?): OutputStream {
            if(value == null) throw RuntimeException("Attempt to encode a null Attribute")
            val ordinal = Attributes.values().firstOrNull{ it.spec == value }?.ordinal
                ?: throw NotImplementedError("Attempt to encode Attribute not in Attributes enum")
            stream.write(ordinal)
            return stream
        }

        override fun decode(stream: InputStream): Attribute? {
            val ordinal = stream.read()
            // TODO: Log error if no match
            return Attributes.values().firstOrNull { it.ordinal == ordinal }?.spec
        }

    }
}