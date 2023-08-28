package io.opencola.model.protobuf

import io.opencola.application.TestApplication
import io.opencola.model.Id
import io.opencola.model.ResourceEntity
import io.opencola.model.SignedTransaction
import io.opencola.model.value.*
import io.opencola.security.generateKeyPair
import io.opencola.serialization.EncodingFormat
import io.opencola.storage.ExposedEntityStoreContext
import io.opencola.storage.addPersona
import io.opencola.util.CompressionFormat
import io.opencola.util.compress
import io.opencola.util.deflate
import org.junit.Test
import java.net.URI
import kotlin.test.assertContentEquals

import kotlin.test.assertEquals


class ModelTest {

    @Test
    fun testBooleanValueSerialization() {
        assertEquals(true, BooleanValue.decodeProto(BooleanValue.encodeProto(true)))
        assertEquals(false, BooleanValue.decodeProto(BooleanValue.encodeProto(false)))
    }

    @Test
    fun testByteArrayValueSerialization() {
        val testBytes = "test".toByteArray()
        assertContentEquals(testBytes, ByteArrayValue.decodeProto(ByteArrayValue.encodeProto(testBytes)))
    }

    @Test
    fun testFloatValueSerialization() {
        val testFloat = 1.0f
        assertEquals(testFloat, FloatValue.decodeProto(FloatValue.encodeProto(testFloat)))
    }

    @Test
    fun testIdValueSerialization() {
        val testId = Id.ofData("test".toByteArray())
        assertEquals(testId, IdValue.decodeProto(IdValue.encodeProto(testId)))
    }

    @Test
    fun testPublicKeyValueSerialization() {
        val testPublicKey = generateKeyPair().public
        assertEquals(testPublicKey, PublicKeyValue.decodeProto(PublicKeyValue.encodeProto(testPublicKey)))
    }

    @Test
    fun testStringValueSerialization() {
        val testString = "test"
        assertEquals(testString, StringValue.decodeProto(StringValue.encodeProto(testString)))
    }

    @Test
    fun testUriValueSerialization() {
        val testUri = URI("http://test.com")
        assertEquals(testUri, UriValue.decodeProto(UriValue.encodeProto(testUri)))
    }

    @Test
    fun testProtobufTransactionSerialization() {
        val context = ExposedEntityStoreContext(TestApplication.getTmpDirectory(".storage"))
        val persona = context.addressBook.addPersona("Persona0", false)
        val entity0 = ResourceEntity(persona.personaId, URI("mock://entity0"), "entity0", "description0", "text0")
        val entity1 = ResourceEntity(persona.personaId, URI("mock://entity1"), "entity1", "description1", "text1")
        val signedTransaction = context.entityStore.updateEntities(entity0, entity1)!!

        val encoded = SignedTransaction.encode(signedTransaction)
        val compressedEncoded = deflate(encoded)

        // Encode with protobuf for comparison
        val transaction = signedTransaction.transaction
        val protoEncoded = signedTransaction.transaction.encodeProto()
        val compressedTransaction = compress(CompressionFormat.DEFLATE, protoEncoded)
        val signature = context.signator.signBytes(transaction.authorityId.toString(), compressedTransaction.bytes)
        val protoSignedTransaction = SignedTransaction(EncodingFormat.PROTOBUF, compressedTransaction, signature)

        val protoPacked = SignedTransaction.encodeProto(protoSignedTransaction)
        val protoCompressed = deflate(protoPacked)
        val protoUnpacked = SignedTransaction.decodeProto(protoPacked)
        assertEquals(protoSignedTransaction, protoUnpacked)

        println("encoded: ${encoded.size} bytes")
        println("compressedEncoded: ${compressedEncoded.size} bytes")
        println("protoPacked: ${protoPacked.size} bytes")
        println("protoCompressed: ${protoCompressed.size} bytes")
    }
}