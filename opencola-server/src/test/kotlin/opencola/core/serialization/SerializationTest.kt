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
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testProtoBufSerialization() {
        val authority by TestApplication.instance.injector.instance<Authority>()
        val fact = Fact(
            authority.authorityId,
            authority.entityId,
            CoreAttribute.Description.spec,
            Value("Test value".toByteArray()),
            Operation.Add,
            0,
            0,
        )

        val protoEncoded = ProtoBuf.encodeToByteArray(fact)
        val jsonEncoded = Json.encodeToString(fact)
        val customEncoded = ByteArrayOutputStream().use {
            Fact.encode(it, fact)
            it.toByteArray()
        }

        val protoDecoded = ProtoBuf.decodeFromByteArray<Fact>(protoEncoded)
        val jsonDecoded = Json.decodeFromString<Fact>(jsonEncoded)
        val customDecoded = ByteArrayInputStream(customEncoded).use { Fact.decode(it) }

        assertEquals(fact, protoDecoded)
        assertEquals(fact, jsonDecoded)
        assertEquals(fact, customDecoded)
    }

    @Test
    fun testUUIDCodec(){
        val uuid = UUID.randomUUID()
        val encoded = UUIDByteArrayCodecCodec.encode(uuid)
        val decoded = UUIDByteArrayCodecCodec.decode(encoded)

        assertEquals(uuid, decoded)
    }
}