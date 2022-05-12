package opencola.core.serialization

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import opencola.core.TestApplication
import opencola.core.model.*
import org.junit.Test
import org.kodein.di.instance
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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