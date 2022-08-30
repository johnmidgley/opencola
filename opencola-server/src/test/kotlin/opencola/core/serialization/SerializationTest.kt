package opencola.core.serialization

import io.opencola.core.serialization.UUIDByteArrayCodecCodec
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class SerializationTest {
    @Test
    fun testUUIDCodec(){
        val uuid = UUID.randomUUID()
        val encoded = UUIDByteArrayCodecCodec.encode(uuid)
        val decoded = UUIDByteArrayCodecCodec.decode(encoded)

        assertEquals(uuid, decoded)
    }
}