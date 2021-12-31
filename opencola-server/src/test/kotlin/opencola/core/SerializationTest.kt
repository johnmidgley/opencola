package opencola.core

import getAuthority
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import opencola.core.model.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class SerializationTest {
    @Test
    fun testProtoBufSerialization(){
        val authority = getAuthority()
        val fact = Fact(authority.entityId, authority.entityId, CoreAttribute.Description.spec, Value("Test value".toByteArray()), Operation.Add, 1)

        val protoEncoded = ProtoBuf.encodeToByteArray(fact)
        val jsonEncoded = Json.encodeToString(fact)
        val customEncoded = ByteArrayOutputStream().use{
            Fact.encode(it, fact)
            it.toByteArray()
        }

        val protoDecoded = ProtoBuf.decodeFromByteArray<Fact>(protoEncoded)
        val jsonDecoded = Json.decodeFromString<Fact>(jsonEncoded)
        val customDecoded = ByteArrayInputStream(customEncoded).use{ Fact.decode(it) }

        assertEquals(fact, protoDecoded)
        assertEquals(fact, jsonDecoded)
        assertEquals(fact, customDecoded)
    }
}