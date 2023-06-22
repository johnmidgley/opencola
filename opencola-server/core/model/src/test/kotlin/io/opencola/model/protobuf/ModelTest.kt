package io.opencola.model.protobuf

import io.opencola.application.TestApplication
import io.opencola.model.ResourceEntity
import io.opencola.model.SignedTransaction
import io.opencola.serialization.EncodingFormat
import io.opencola.storage.ExposedEntityStoreContext
import io.opencola.storage.addPersona
import io.opencola.util.deflate
import org.junit.Test
import java.net.URI

import kotlin.test.assertEquals


class ModelTest {
    @Test
    fun testProtobufSerialization() {
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
        val signature = context.signator.signBytes(transaction.authorityId.toString(), protoEncoded)
        val protoSignedTransaction = SignedTransaction(EncodingFormat.PROTOBUF, protoEncoded, signature)

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